package com.domidodo.logx.engine.processor.consumer;

import com.domidodo.logx.common.context.TenantContext;
import com.domidodo.logx.engine.processor.parser.LogParser;
import com.domidodo.logx.engine.processor.writer.ElasticsearchWriter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 日志 Kafka 消费者（完全修复版）
 */
@Slf4j
@Component
public class LogKafkaConsumer {

    @Autowired
    private LogParser logParser;

    @Autowired
    private ElasticsearchWriter elasticsearchWriter;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Value("${logx.kafka.topic.log-ingestion:logx-logs}")
    private String logTopic;

    @Value("${logx.kafka.topic.dead-letter:logx-logs-dlq}")
    private String deadLetterTopic;

    @Value("${logx.consumer.max-retries:3}")
    private int maxRetries;

    @Value("${logx.consumer.retry-backoff-ms:1000}")
    private long retryBackoffMs;

    /**
     * ✅ 批量消费日志（完全修复版）
     */
    @KafkaListener(
            topics = "${logx.kafka.topic.log-ingestion:logx-logs}",
            groupId = "${spring.kafka.consumer.group-id:logx-processor-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeLogs(List<String> messages, Acknowledgment acknowledgment) {
        long startTime = System.currentTimeMillis();

        try {
            log.debug("Received {} log messages from Kafka", messages.size());

            if (messages.isEmpty()) {
                acknowledgment.acknowledge();
                return;
            }

            // 1. 解析所有日志
            ParseResult parseResult = parseMessages(messages);

            if (parseResult.validLogs.isEmpty()) {
                log.warn("No valid logs to process after parsing");

                // 解析失败的发送到死信队列
                if (!parseResult.failedMessages.isEmpty()) {
                    sendToDeadLetterQueue(parseResult.failedMessages, "Parse failed");
                }

                acknowledgment.acknowledge();
                return;
            }

            // 2. 批量写入 Elasticsearch（带重试）
            boolean writeSuccess = writeWithRetry(parseResult.validLogs);

            if (writeSuccess) {
                // 成功：提交offset
                acknowledgment.acknowledge();

                // 解析失败的发送到死信队列
                if (!parseResult.failedMessages.isEmpty()) {
                    sendToDeadLetterQueue(parseResult.failedMessages, "Parse failed");
                }

                long duration = System.currentTimeMillis() - startTime;
                log.info("Processed {} logs: {} valid, {} parse failed, took {}ms",
                        messages.size(), parseResult.validLogs.size(),
                        parseResult.failedMessages.size(), duration);

            } else {
                // 写入失败：发送所有消息到死信队列，然后提交offset
                log.error("Failed to write logs after {} retries, sending to DLQ", maxRetries);
                sendToDeadLetterQueue(messages, "Write to ES failed after " + maxRetries + " retries");

                // 提交offset，避免阻塞后续消息
                acknowledgment.acknowledge();
            }

            // 3. 记录指标
            int successCount = writeSuccess ? parseResult.validLogs.size() : 0;
            int failCount = messages.size() - successCount;
            recordMetrics(successCount, failCount);

        } catch (Exception e) {
            log.error("Error processing logs batch", e);

            // 异常情况：发送到死信队列并提交offset
            sendToDeadLetterQueue(messages, "Exception: " + e.getMessage());
            acknowledgment.acknowledge();

            recordMetrics(0, messages.size());
        }
    }

    /**
     * 解析结果
     */
    private static class ParseResult {
        List<Map<String, Object>> validLogs = new ArrayList<>();
        List<String> failedMessages = new ArrayList<>();
    }

    /**
     * 解析所有消息
     */
    private ParseResult parseMessages(List<String> messages) {
        ParseResult result = new ParseResult();

        for (String message : messages) {
            try {
                Map<String, Object> logOne = logParser.parse(message);
                if (logOne != null && !logOne.isEmpty()) {
                    result.validLogs.add(logOne);
                } else {
                    log.warn("Parsed log is empty: {}", message);
                    result.failedMessages.add(message);
                }
            } catch (Exception e) {
                log.error("Failed to parse log message", e);
                result.failedMessages.add(message);
            }
        }

        return result;
    }

    /**
     * 带重试的写入 Elasticsearch
     */
    private boolean writeWithRetry(List<Map<String, Object>> logs) {
        int retryCount = 0;

        while (retryCount <= maxRetries) {
            try {
                int successCount = elasticsearchWriter.bulkWrite(logs);

                if (successCount == logs.size()) {
                    // 全部成功
                    return true;
                } else if (successCount > 0) {
                    // 部分成功，继续重试失败的部分
                    log.warn("Partial write success: {}/{}, retry {}/{}",
                            successCount, logs.size(), retryCount, maxRetries);
                } else {
                    // 全部失败
                    log.error("All writes failed, retry {}/{}", retryCount, maxRetries);
                }

            } catch (Exception e) {
                log.error("Write to ES failed, retry {}/{}", retryCount, maxRetries, e);
            }

            // 重试前等待（指数退避）
            if (retryCount < maxRetries) {
                retryCount++;
                long backoff = retryBackoffMs * (1L << (retryCount - 1)); // 指数退避: 1s, 2s, 4s
                long actualBackoff = Math.min(backoff, 10000); // 最多等待10秒

                try {
                    log.debug("Waiting {}ms before retry {}", actualBackoff, retryCount);
                    Thread.sleep(actualBackoff);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Retry interrupted", e);
                    return false;
                }
            } else {
                break;
            }
        }

        return false;
    }

    /**
     * 发送到死信队列
     */
    private void sendToDeadLetterQueue(List<String> messages, String reason) {
        if (kafkaTemplate == null) {
            log.warn("KafkaTemplate not available, cannot send to DLQ");
            return;
        }

        if (messages == null || messages.isEmpty()) {
            return;
        }

        try {
            int successCount = 0;
            for (String message : messages) {
                try {
                    kafkaTemplate.send(deadLetterTopic, reason, message)
                            .whenComplete((result, ex) -> {
                                if (ex != null) {
                                    log.error("Failed to send message to DLQ", ex);
                                }
                            });
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to send message to DLQ", e);
                }
            }

            log.info("Sent {}/{} messages to dead letter queue ({}): {}",
                    successCount, messages.size(), reason, deadLetterTopic);
        } catch (Exception e) {
            log.error("Failed to send messages to DLQ", e);
        }
    }

    /**
     * 记录处理指标
     */
    private void recordMetrics(int successCount, int failCount) {
        if (meterRegistry != null) {
            try {
                // 成功计数
                meterRegistry.counter("logx.kafka.consumer.success",
                                "tenant", String.valueOf(TenantContext.getTenantId()))
                        .increment(successCount);

                // 失败计数
                meterRegistry.counter("logx.kafka.consumer.failed",
                                "tenant", String.valueOf(TenantContext.getTenantId()))
                        .increment(failCount);

                // 处理速率
                meterRegistry.gauge("logx.kafka.consumer.last.batch.size", successCount);

                log.debug("Metrics recorded: success={}, failed={}", successCount, failCount);
            } catch (Exception e) {
                log.warn("Failed to record metrics", e);
            }
        }
    }
}