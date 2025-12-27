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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
//                        if (ex != null) {
//                            log.error("无法将日志发送到Kafka", ex);
//                        } else {
//                            log.debug("日志发送到Kafka：分区={}，偏移量={}",
//                                    result.getRecordMetadata().partition(),
//                                    result.getRecordMetadata().offset());
//                        }
                    });

            return true;

        } catch (Exception e) {
            log.error("无法将日志发送到Kafka", e);
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

        // 1. 准备所有发送任务
        List<CompletableFuture<SendResult<String, String>>> futures = logs.stream()
                .map(logOne -> {
                    try {
                        String logJson = JsonUtil.toJson(logOne);
                        String key = generateKey(logOne);
                        return kafkaTemplate.send(logTopic, key, logJson);
                    } catch (Exception e) {
                        log.error("准备日志发送失败", e);
                        return CompletableFuture.<SendResult<String, String>>failedFuture(e);
                    }
                })
                .toList();

        // 2. 等待所有任务完成（带超时）
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS); // 添加超时
        } catch (TimeoutException e) {
            log.error("30秒后批量发送超时", e);
        } catch (Exception e) {
            log.error("批量发送失败", e);
        }

        // 3. 统计成功数量（不阻塞）
        for (CompletableFuture<SendResult<String, String>> future : futures) {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                successCount.incrementAndGet();
            }
        }

        int total = logs.size();
        int failed = total - successCount.get();
        log.info("批量发送完成：总计={}，成功={}、失败={}",
                total, successCount.get(), failed);

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
            log.error("异步发送日志失败", e);
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