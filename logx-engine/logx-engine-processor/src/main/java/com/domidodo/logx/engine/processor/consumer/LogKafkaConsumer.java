package com.domidodo.logx.engine.processor.consumer;

import com.domidodo.logx.engine.processor.parser.LogParser;
import com.domidodo.logx.engine.processor.writer.ElasticsearchWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 日志 Kafka 消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogKafkaConsumer {

    private final LogParser logParser;
    private final ElasticsearchWriter elasticsearchWriter;

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
        int successCount = 0;
        int failCount = 0;

        try {
            log.debug("Received {} log messages from Kafka", messages.size());

            // 1. 解析日志
            List<Map<String, Object>> parsedLogs = messages.stream()
                    .map(this::parseLogSafely)
                    .filter(log -> log != null && !log.isEmpty())
                    .collect(Collectors.toList());

            if (parsedLogs.isEmpty()) {
                log.warn("No valid logs to process after parsing");
                acknowledgment.acknowledge();
                return;
            }

            // 2. 批量写入 Elasticsearch
            successCount = elasticsearchWriter.bulkWrite(parsedLogs);
            failCount = parsedLogs.size() - successCount;

            // 3. 手动提交 offset
            acknowledgment.acknowledge();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Processed {} logs: {} success, {} failed, took {}ms",
                    messages.size(), successCount, failCount, duration);

        } catch (Exception e) {
            log.error("Error processing logs batch", e);
            failCount = messages.size();
            // 不提交offset，会重新消费
        }

        // 4. 记录指标
        recordMetrics(successCount, failCount);
    }

    /**
     * 安全解析日志（捕获异常）
     */
    private Map<String, Object> parseLogSafely(String message) {
        try {
            return logParser.parse(message);
        } catch (Exception e) {
            log.error("Failed to parse log message: {}", message, e);
            return null;
        }
    }

    /**
     * 记录处理指标
     */
    private void recordMetrics(int successCount, int failCount) {
        // TODO: 集成 Prometheus/Micrometer 记录指标
        // 可以记录：
        // - 处理速率（logs/second）
        // - 成功率
        // - 失败率
        // - 处理延迟
    }
}