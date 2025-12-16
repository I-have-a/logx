package com.domidodo.gateway.http.service;


import com.domidodo.logx.common.constant.SystemConstant;
import com.domidodo.logx.common.context.TenantContext;
import com.domidodo.logx.common.dto.LogDTO;
import com.domidodo.logx.common.exception.BusinessException;
import com.domidodo.logx.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
    public void ingestBatch(List<LogDTO> logs) {
        if (logs == null || logs.isEmpty()) {
            throw new BusinessException("日志列表不能为空");
        }

        logs.forEach(this::ingest);
        log.info("批量接收完成: {} 条", logs.size());
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
            String topic = SystemConstant.KAFKA_TOPIC_LOGS_RAW;
            String key = logDTO.getTenantId() + ":" + logDTO.getSystemId();

            kafkaTemplate.send(topic, key, json);
            log.debug("日志已发送到 Kafka: {}", logDTO.getId());
        } catch (Exception e) {
            log.error("发送日志到 Kafka 失败", e);
            throw new BusinessException("日志接收失败");
        }
    }
}
