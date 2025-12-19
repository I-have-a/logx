package com.domidodo.logx.engine.detection.alerts;

import com.domidodo.logx.common.enums.AlertLevelEnum;
import com.domidodo.logx.engine.detection.entity.Alert;
import com.domidodo.logx.engine.detection.entity.Rule;
import com.domidodo.logx.engine.detection.mapper.AlertMapper;
import com.domidodo.logx.engine.detection.rules.EnhancedRuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertMapper alertMapper;
    private final EnhancedRuleEngine enhancedRuleEngine;
    private final NotificationService notificationService;

    /**
     * 触发告警（异步执行）
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void triggerAlert(Rule rule, Map<String, Object> logData) {
        try {
            // 1. 创建告警记录
            Alert alert = createAlert(rule, logData);

            // 2. 保存到数据库
            alertMapper.insert(alert);

            // 3. 发送通知
            AlertLevelEnum level = AlertLevelEnum.fromCode(rule.getAlertLevel());
            if (level.isImmediateNotify()) {
                // 严重告警立即发送
                notificationService.sendImmediate(alert);
            } else {
                // 其他告警加入队列，批量发送
                notificationService.addToQueue(alert);
            }

            log.info("Alert triggered: ruleId={}, tenantId={}, systemId={}, level={}",
                    rule.getId(), rule.getTenantId(), rule.getSystemId(), rule.getAlertLevel());

        } catch (Exception e) {
            log.error("Failed to trigger alert: rule={}", rule.getRuleName(), e);
        }
    }

    /**
     * 创建告警记录
     */
    private Alert createAlert(Rule rule, Map<String, Object> logData) {
        Alert alert = new Alert();

        // 基础信息
        alert.setTenantId(rule.getTenantId());
        alert.setSystemId(rule.getSystemId());
        alert.setRuleId(rule.getId());

        // 告警信息
        alert.setAlertLevel(rule.getAlertLevel());
        alert.setAlertType(rule.getRuleType());
        alert.setAlertContent(enhancedRuleEngine.generateAlertContent(rule, logData));
        alert.setTriggerTime(LocalDateTime.now());

        // 状态
        alert.setStatus("PENDING");

        return alert;
    }

    /**
     * 处理告警
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleAlert(Long alertId, String handleUser, String handleRemark) {
        Alert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            log.warn("Alert not found: id={}", alertId);
            return;
        }

        alert.setStatus("RESOLVED");
        alert.setHandleUser(handleUser);
        alert.setHandleTime(LocalDateTime.now());
        alert.setHandleRemark(handleRemark);

        alertMapper.updateById(alert);
        log.info("Alert handled: id={}, user={}", alertId, handleUser);
    }

    /**
     * 查询待处理告警
     */
    public List<Alert> getPendingAlerts(String tenantId) {
        return alertMapper.selectPendingAlerts(tenantId);
    }

    /**
     * 查询最近的告警
     */
    public List<Alert> getRecentAlerts(String tenantId, String systemId, int hours, int limit) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);
        return alertMapper.selectRecentAlerts(tenantId, systemId, startTime, limit);
    }

    /**
     * 统计告警数量
     */
    public long countAlerts(String tenantId, LocalDateTime startTime, LocalDateTime endTime) {
        return alertMapper.countAlerts(tenantId, startTime, endTime);
    }

    /**
     * 批量标记为已读
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(List<Long> alertIds) {
        for (Long alertId : alertIds) {
            Alert alert = alertMapper.selectById(alertId);
            if (alert != null && "PENDING".equals(alert.getStatus())) {
                alert.setStatus("PROCESSING");
                alertMapper.updateById(alert);
            }
        }
        log.info("Marked {} alerts as read", alertIds.size());
    }
}