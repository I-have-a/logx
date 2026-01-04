package com.domidodo.logx.engine.detection.alerts;

import com.domidodo.logx.common.enums.AlertLevelEnum;
import com.domidodo.logx.engine.detection.entity.Alert;
import com.domidodo.logx.engine.detection.entity.Rule;
import com.domidodo.logx.engine.detection.mapper.AlertMapper;
import com.domidodo.logx.engine.detection.mapper.RuleMapper;
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
 * 支持静默期、告警聚合、升级突破等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertMapper alertMapper;
    private final RuleMapper ruleMapper;
    private final EnhancedRuleEngine enhancedRuleEngine;
    private final NotificationService notificationService;
    private final AlertSilenceManager silenceManager;

    /**
     * 触发告警（异步执行）
     * <p>
     * 处理流程：
     * 1. 检查静默期
     * 2. 检查是否需要升级突破
     * 3. 创建告警记录
     * 4. 发送通知
     * 5. 记录静默状态
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void triggerAlert(Rule rule, Map<String, Object> logData) {
        try {
            String targetKey = extractTargetKey(rule, logData);
            String alertLevel = rule.getAlertLevel();

            // 1. 检查静默期
            boolean inSilence = silenceManager.isInSilencePeriod(
                    rule.getId(),
                    rule.getTenantId(),
                    rule.getSystemId(),
                    rule.getEffectiveSilenceScope(),
                    rule.getEffectiveSilencePeriod(),
                    targetKey
            );

            if (inSilence) {
                // 2. 检查是否允许升级突破
                if (rule.isAllowEscalation()) {
                    boolean shouldEscalate = silenceManager.shouldEscalate(
                            rule.getId(),
                            rule.getTenantId(),
                            rule.getSystemId(),
                            rule.getEffectiveSilenceScope(),
                            targetKey,
                            alertLevel
                    );

                    if (shouldEscalate) {
                        log.info("告警级别升级，突破静默期: ruleId={}, newLevel={}",
                                rule.getId(), alertLevel);
                        // 继续执行告警流程
                    } else {
                        // 在静默期内且未升级，跳过告警
                        log.debug("告警被静默抑制: ruleId={}, targetKey={}",
                                rule.getId(), targetKey);
                        updateTriggerCount(rule);
                        return;
                    }
                } else {
                    // 不允许升级，直接跳过
                    log.debug("告警被静默抑制: ruleId={}, targetKey={}",
                            rule.getId(), targetKey);
                    updateTriggerCount(rule);
                    return;
                }
            }

            // 3. 创建告警记录
            Alert alert = createAlert(rule, logData, targetKey);

            // 4. 保存到数据库
            alertMapper.insert(alert);

            // 5. 更新规则触发统计
            updateTriggerCount(rule);

            // 6. 记录静默状态（开始新的静默期）
            silenceManager.recordAlertTriggered(
                    rule.getId(),
                    rule.getTenantId(),
                    rule.getSystemId(),
                    rule.getEffectiveSilenceScope(),
                    targetKey,
                    alertLevel
            );

            // 7. 发送通知
            sendNotification(rule, alert);

            log.info("已触发告警: ruleId={}, alertId={}, tenantId={}, systemId={}, level={}",
                    rule.getId(), alert.getId(), rule.getTenantId(),
                    rule.getSystemId(), rule.getAlertLevel());

        } catch (Exception e) {
            log.error("触发告警失败: rule={}", rule.getRuleName(), e);
        }
    }

    /**
     * 创建告警记录
     */
    private Alert createAlert(Rule rule, Map<String, Object> logData, String targetKey) {
        Alert alert = new Alert();

        // 基础信息
        alert.setTenantId(rule.getTenantId());
        alert.setSystemId(rule.getSystemId());
        alert.setRuleId(rule.getId());

        // 告警信息
        alert.setAlertLevel(rule.getAlertLevel());
        alert.setAlertType(rule.getRuleType());

        // 生成告警内容（包含聚合信息）
        String content = enhancedRuleEngine.generateAlertContent(rule, logData);

        // 如果有被抑制的告警，添加聚合摘要
        if (rule.isEnableAggregation()) {
            AlertSilenceManager.AlertAggregation aggregation = silenceManager.getAggregation(
                    rule.getId(),
                    rule.getTenantId(),
                    rule.getSystemId(),
                    rule.getEffectiveSilenceScope(),
                    targetKey
            );
            if (aggregation != null && aggregation.getCount() > 0) {
                content += "\n\n【聚合信息】\n" + aggregation.getSummary();
            }
        }

        alert.setAlertContent(content);
        alert.setTriggerTime(LocalDateTime.now());

        // 状态
        alert.setStatus("PENDING");

        return alert;
    }

    /**
     * 发送通知
     */
    private void sendNotification(Rule rule, Alert alert) {
        AlertLevelEnum level = AlertLevelEnum.fromCode(rule.getAlertLevel());
        if (level.isImmediateNotify()) {
            // 严重告警立即发送
            notificationService.sendImmediate(alert);
        } else {
            // 其他告警加入队列，批量发送
            notificationService.addToQueue(alert);
        }
    }

    /**
     * 更新规则触发统计
     */
    private void updateTriggerCount(Rule rule) {
        try {
            // 使用数据库原子操作更新
            ruleMapper.incrementTriggerCount(rule.getId());
        } catch (Exception e) {
            log.warn("更新触发统计失败: ruleId={}", rule.getId(), e);
        }
    }

    /**
     * 提取目标Key（用于静默粒度）
     */
    private String extractTargetKey(Rule rule, Map<String, Object> logData) {
        String scope = rule.getEffectiveSilenceScope();

        switch (scope.toUpperCase()) {
            case "TARGET" -> {
                // 根据监控目标提取 key
                String target = rule.getMonitorTarget();
                if (target != null) {
                    if (target.startsWith("userId:")) {
                        return "user:" + logData.get("userId");
                    } else if (target.startsWith("module:")) {
                        return "module:" + logData.get("module");
                    } else if (target.startsWith("ip:")) {
                        return "ip:" + logData.get("ip");
                    } else if (target.startsWith("operation:")) {
                        return "operation:" + logData.get("operation");
                    } else if (target.contains("/")) {
                        return "url:" + logData.get("requestUrl");
                    }
                }
                return "target:" + target;
            }
            case "USER" -> {
                Object userId = logData.get("userId");
                return userId != null ? "user:" + userId : "user:unknown";
            }
            default -> {
                // RULE 级别不需要额外的 key
                return "";
            }
        }
    }

    /**
     * 处理告警
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleAlert(Long alertId, String handleUser, String handleRemark) {
        Alert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            log.warn("未找到告警: id={}", alertId);
            return;
        }

        alert.setStatus("RESOLVED");
        alert.setHandleUser(handleUser);
        alert.setHandleTime(LocalDateTime.now());
        alert.setHandleRemark(handleRemark);

        alertMapper.updateById(alert);
        log.info("已处理告警: id={}, user={}", alertId, handleUser);
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
        log.info("将 {} 个告警标记为已读", alertIds.size());
    }

    /**
     * 重置规则的静默期（管理操作）
     */
    public void resetRuleSilence(Long ruleId) {
        silenceManager.resetRuleSilence(ruleId);
        log.info("已重置规则的静默期: ruleId={}", ruleId);
    }

    /**
     * 获取静默状态统计
     */
    public Map<String, Object> getSilenceStatistics() {
        return silenceManager.getStatistics();
    }
}