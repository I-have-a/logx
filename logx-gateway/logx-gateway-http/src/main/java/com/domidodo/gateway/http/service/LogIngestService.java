package com.domidodo.gateway.http.service;


import com.domidodo.logx.common.constant.SystemConstant;
import com.domidodo.logx.common.context.TenantContext;
import com.domidodo.logx.common.dto.LogDTO;
import com.domidodo.logx.common.exception.BusinessException;
import com.domidodo.logx.common.result.Result;
import com.domidodo.logx.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class LogIngestService {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 接收单条日志
     */
    public void ingest(LogDTO logDTO) {
        enrichLog(logDTO);
        sendToKafka(logDTO);
    }

    /**
     * 批量接收
     */
    public Result<Map<String, Object>> ingestBatch(List<LogDTO> logs) {
        if (logs == null || logs.isEmpty()) {
            throw new BusinessException("日志列表不能为空");
        }

        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < logs.size(); i++) {
            try {
                ingest(logs.get(i));
                successCount++;
            } catch (Exception e) {
                failCount++;
                errors.add("Index " + i + ": " + e.getMessage());
                log.error("Failed to ingest log at index {}", i, e);
            }
        }

        log.info("批量接收完成: 总数={}, 成功={}, 失败={}",
                logs.size(), successCount, failCount);
        HashMap<String, Object> map = new HashMap<>();
        map.put("successCount", successCount);
        map.put("failCount", failCount);
        map.put("errors", errors);
        map.put("totalCount", logs.size());
        return Result.success(map);
    }

    /**
     * 补充日志元数据
     */
    private void enrichLog(LogDTO logDTO) {
        if (logDTO.getId() == null) {
            logDTO.setId(UUID.randomUUID().toString().replace("-", ""));
        }

        if (logDTO.getTenantId() == null) {
            logDTO.setTenantId(TenantContext.getTenantId());
        }

        if (logDTO.getTimestamp() == null) {
            logDTO.setTimestamp(LocalDateTime.now());
        }
    }

    /**
     * 发送到 Kafka
     */
    private void sendToKafka(LogDTO logDTO) {
        try {
            String json = JsonUtil.toJson(logDTO);
            String topic = SystemConstant.KAFKA_TOPIC_LOGS;
            String key = generateKey(logDTO);

            kafkaTemplate.send(topic, key, json);
            log.debug("日志已发送到 Kafka: {}", logDTO.getId());
        } catch (Exception e) {
            log.error("发送日志到 Kafka 失败", e);
            throw new BusinessException("日志接收失败");
        }
    }


    /**
     * 生成 Kafka 消息 Key
     * 格式：{tenantId}:{systemId}:{traceId}
     */
    private String generateKey(LogDTO log) {
        String tenantId = String.valueOf(log.getTenantId());
        String systemId = String.valueOf(log.getSystemId());
        String traceId = log.getTraceId();

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
