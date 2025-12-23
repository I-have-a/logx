package com.domidodo.logx.engine.processor.consumer;

import com.domidodo.logx.common.context.TenantContext;
import com.domidodo.logx.common.util.JsonUtil;
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
import java.util.concurrent.CompletableFuture;

/**
 * 日志 Kafka 消费者（修复版）
 * <p>
 * 数据流：
 * Gateway → logx-logs → Processor → ES + logx-logs-processing → Detection
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

    @Value("${logx.kafka.topic.log-processing:logx-logs-processing}")
    private String processingTopic;

    @Value("${logx.kafka.topic.dead-letter:logx-logs-dlq}")
    private String deadLetterTopic;

    @Value("${logx.consumer.max-retries:3}")
    private int maxRetries;

    @Value("${logx.consumer.retry-backoff-ms:1000}")
    private long retryBackoffMs;

    /**
     * 批量消费日志
     */
    @KafkaListener(
            topics = "${logx.kafka.topic.log-ingestion:logx-logs}",
            groupId = "${spring.kafka.consumer.group-id:logx-processor-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeLogs(List<String> messages, Acknowledgment acknowledgment) {
        long startTime = System.currentTimeMillis();

        try {
            log.debug("收到来自Kafka的 {} 条日志消息", messages.size());

            if (messages.isEmpty()) {
                acknowledgment.acknowledge();
                return;
            }

            // 1. 解析所有日志
            ParseResult parseResult = parseMessages(messages);

            if (parseResult.validLogs.isEmpty()) {
                log.warn("解析后没有要处理的有效日志");

                // 解析失败的发送到死信队列
                if (!parseResult.failedMessages.isEmpty()) {
                    sendToDeadLetterQueue(parseResult.failedMessages, "解析失败");
                }

                acknowledgment.acknowledge();
                return;
            }

            // 2. 批量写入 Elasticsearch
            boolean writeSuccess = writeWithRetry(parseResult.validLogs);

            // 3. 【关键修复】转发到 Detection 模块
            boolean forwardSuccess = false;
            if (writeSuccess) {
                forwardSuccess = forwardToDetection(parseResult.validLogs);
            }

            if (writeSuccess && forwardSuccess) {
                // 全部成功：提交offset
                acknowledgment.acknowledge();

                // 解析失败的发送到死信队列
                if (!parseResult.failedMessages.isEmpty()) {
                    sendToDeadLetterQueue(parseResult.failedMessages, "解析失败");
                }

                long duration = System.currentTimeMillis() - startTime;
                log.info("已处理 {} 个日志：{} 个有效，{} 个解析失败，耗时 {} 毫秒",
                        messages.size(), parseResult.validLogs.size(),
                        parseResult.failedMessages.size(), duration);

            } else {
                // 失败处理
                if (!writeSuccess) {
                    log.error("{} 次重试后，无法将日志写入ES", maxRetries);
                }
                if (!forwardSuccess) {
                    log.error("未能将日志转发到检测模块");
                }

                // 发送到死信队列
                sendToDeadLetterQueue(messages, "写入ES或转发失败");

                // 提交offset，避免阻塞后续消息
                acknowledgment.acknowledge();
            }

            // 4. 记录指标
            int successCount = (writeSuccess && forwardSuccess) ? parseResult.validLogs.size() : 0;
            int failCount = messages.size() - successCount;
            recordMetrics(successCount, failCount);

        } catch (Exception e) {
            log.error("处理日志批时出错", e);

            // 异常情况：发送到死信队列并提交offset
            sendToDeadLetterQueue(messages, "异常: " + e.getMessage());
            acknowledgment.acknowledge();

            recordMetrics(0, messages.size());
        }
    }

    /**
     * 【关键方法】转发到 Detection 模块
     */
    private boolean forwardToDetection(List<Map<String, Object>> logs) {
        if (kafkaTemplate == null) {
            log.error("KafkaTemplate不可用，无法转发到检测");
            return false;
        }

        try {
            List<CompletableFuture<?>> futures = new ArrayList<>();

            for (Map<String, Object> logOne : logs) {
                try {
                    // 转换为JSON
                    String logJson = JsonUtil.toJson(logOne);

                    // 生成Key（保证相同tenantId/systemId的日志在同一分区）
                    String key = generateKey(logOne);

                    // 异步发送
                    CompletableFuture<?> future = kafkaTemplate.send(processingTopic, key, logJson)
                            .whenComplete((result, ex) -> {
                                if (ex != null) {
                                    log.error("未能将日志转发到检测模块：{}", ex.getMessage());
                                }
                            });

                    futures.add(future);

                } catch (Exception e) {
                    log.error("无法准备日志以进行转发", e);
                }
            }

            // 等待所有发送完成（超时30秒）
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);

            // 统计成功数
            long successCount = futures.stream()
                    .filter(f -> f.isDone() && !f.isCompletedExceptionally())
                    .count();

            boolean allSuccess = successCount == logs.size();

            log.info("转发到检测模块：{}/{}日志成功",
                    successCount, logs.size());

            return allSuccess;

        } catch (Exception e) {
            log.error("未能将日志转发到检测模块", e);
            return false;
        }
    }

    /**
     * 生成 Kafka Key
     */
    private String generateKey(Map<String, Object> log) {
        String tenantId = String.valueOf(log.get("tenantId"));
        String systemId = String.valueOf(log.get("systemId"));
        String traceId = String.valueOf(log.get("traceId"));

        return String.format("%s:%s:%s", tenantId, systemId, traceId);
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
                    log.warn("解析日志为空：{}", message);
                    result.failedMessages.add(message);
                }
            } catch (Exception e) {
                log.error("解析日志消息失败", e);
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
                    log.warn("部分写入成功：{}/{}，重试{}/{}",
                            successCount, logs.size(), retryCount, maxRetries);
                } else {
                    // 全部失败
                    log.error("所有写入都失败，请重试{}/{}", retryCount, maxRetries);
                }

            } catch (Exception e) {
                log.error("写入ES失败，请重试{}/{}", retryCount, maxRetries, e);
            }

            // 重试前等待（指数退避）
            if (retryCount < maxRetries) {
                retryCount++;
                long backoff = retryBackoffMs * (1L << (retryCount - 1)); // 指数退避: 1s, 2s, 4s
                long actualBackoff = Math.min(backoff, 10000); // 最多等待10秒

                try {
                    log.debug("{} 次重试前等待毫秒 {}", actualBackoff, retryCount);
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
            log.warn("KafkaTemplate不可用，无法发送到DLQ");
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
                                    log.error("向DLQ发送消息失败", ex);
                                }
                            });
                    successCount++;
                } catch (Exception e) {
                    log.error("向DLQ发送消息失败", e);
                }
            }

            log.info("已将{}/{}消息发送到死信队列（{}）：{}",
                    successCount, messages.size(), reason, deadLetterTopic);
        } catch (Exception e) {
            log.error("向DLQ发送消息失败", e);
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

                log.debug("记录的指标：成功={}，失败={}", successCount, failCount);
            } catch (Exception e) {
                log.warn("未能记录指标", e);
            }
        }
    }
}