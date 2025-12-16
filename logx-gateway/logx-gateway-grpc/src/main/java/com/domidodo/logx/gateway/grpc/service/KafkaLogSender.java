package com.domidodo.logx.gateway.grpc.service;

import com.domidodo.logx.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka 日志发送服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaLogSender {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${logx.kafka.topic.log-ingestion:logx-logs}")
    private String logTopic;

    /**
     * 发送单条日志
     *
     * @param logOne 日志数据
     * @return 是否成功
     */
    public boolean send(Map<String, Object> logOne) {
        try {
            String logJson = JsonUtil.toJson(logOne);
            String key = generateKey(logOne);

            kafkaTemplate.send(logTopic, key, logJson)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send log to Kafka", ex);
                        } else {
                            log.debug("Log sent to Kafka: partition={}, offset={}",
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });

            return true;

        } catch (Exception e) {
            log.error("Failed to send log to Kafka", e);
            return false;
        }
    }

    /**
     * 批量发送日志
     *
     * @param logs 日志列表
     * @return 成功发送的数量
     */
    public int sendBatch(List<Map<String, Object>> logs) {
        if (logs == null || logs.isEmpty()) {
            return 0;
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 使用 CompletableFuture 批量发送
        List<CompletableFuture<SendResult<String, String>>> futures = logs.stream()
                .map(logOne -> {
                    try {
                        String logJson = JsonUtil.toJson(logOne);
                        String key = generateKey(logOne);
                        return kafkaTemplate.send(logTopic, key, logJson);
                    } catch (Exception e) {
                        log.error("Failed to prepare log for sending", e);
                        failCount.incrementAndGet();
                        return CompletableFuture.<SendResult<String, String>>failedFuture(e);
                    }
                })
                .toList();

        // 等待所有发送完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        log.error("Batch send failed", ex);
                    }
                });

        // 统计结果
        for (CompletableFuture<SendResult<String, String>> future : futures) {
            try {
                future.get(); // 等待结果
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
                log.error("Failed to send log", e);
            }
        }

        int total = logs.size();
        log.info("Batch send completed: total={}, success={}, failed={}",
                total, successCount.get(), failCount.get());

        return successCount.get();
    }

    /**
     * 异步发送日志（不等待结果）
     *
     * @param logOne 日志数据
     */
    public void sendAsync(Map<String, Object> logOne) {
        try {
            String logJson = JsonUtil.toJson(logOne);
            String key = generateKey(logOne);

            kafkaTemplate.send(logTopic, key, logJson);

        } catch (Exception e) {
            log.error("Failed to send log async", e);
        }
    }

    /**
     * 生成 Kafka 消息 Key
     * 格式：{tenantId}:{systemId}:{traceId}
     */
    private String generateKey(Map<String, Object> log) {
        String tenantId = (String) log.get("tenantId");
        String systemId = (String) log.get("systemId");
        String traceId = (String) log.get("traceId");

        StringBuilder key = new StringBuilder();
        if (tenantId != null) {
            key.append(tenantId);
        }
        key.append(":");
        if (systemId != null) {
            key.append(systemId);
        }
        key.append(":");
        if (traceId != null) {
            key.append(traceId);
        }

        return key.toString();
    }
}