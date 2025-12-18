package com.domidodo.logx.engine.detection.rules;

import com.domidodo.logx.common.context.TenantContext;
import com.domidodo.logx.engine.detection.alerts.AlertService;
import com.domidodo.logx.engine.detection.entity.Rule;
import com.domidodo.logx.engine.detection.mapper.RuleMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.domidodo.logx.common.util.JsonUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则执行器
 * 消费Kafka日志，执行规则检测
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleExecutor {

    private final RuleMapper ruleMapper;
    private final RuleEngine ruleEngine;
    private final AlertService alertService;

    /**
     * 规则缓存：key=tenantId:systemId, value=规则列表
     */
    private final Map<String, List<Rule>> ruleCache = new ConcurrentHashMap<>();

    /**
     * 初始化加载规则
     */
    @PostConstruct
    public void init() {
        loadRules();
        log.info("RuleExecutor initialized, loaded {} tenant-system rules", ruleCache.size());
    }

    /**
     * 消费日志并执行规则检测
     */
    @KafkaListener(
            topics = "${logx.kafka.topic.log-processing:logx-logs-processing}",
            groupId = "${spring.kafka.consumer.group-id:logx-detection-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processLogs(List<String> messages, Acknowledgment acknowledgment) {
        int totalCount = messages.size();
        int matchedCount = 0;

        try {
            for (String message : messages) {
                try {
                    // 1. 解析日志
                    Map<String, Object> logData = JsonUtil.parseObject(message);
                    if (logData == null) {
                        continue;
                    }

                    // 2. 提取租户和系统信息
                    String tenantId = (String) logData.get("tenantId");
                    String systemId = (String) logData.get("systemId");

                    if (tenantId == null || systemId == null) {
                        continue;
                    }

                    // 3. 获取规则
                    String cacheKey = tenantId + ":" + systemId;
                    List<Rule> rules = ruleCache.get(cacheKey);

                    if (rules == null || rules.isEmpty()) {
                        // 尝试从数据库加载
                        rules = ruleMapper.selectEnabledRulesBySystem(tenantId, systemId);
                        if (!rules.isEmpty()) {
                            ruleCache.put(cacheKey, rules);
                        } else {
                            continue;
                        }
                    }

                    // 4. 执行规则匹配
                    for (Rule rule : rules) {
                        if (matchRule(rule, logData)) {
                            matchedCount++;
                            // 触发告警
                            alertService.triggerAlert(rule, logData);
                        }
                    }

                } catch (Exception e) {
                    log.error("Error processing log message", e);
                }
            }

            // 5. 手动提交offset
            acknowledgment.acknowledge();

            log.debug("Processed {} logs, {} matched rules", totalCount, matchedCount);

        } catch (Exception e) {
            log.error("Error processing log batch", e);
            // 不提交offset，会重新消费
        }
    }

    /**
     * 匹配规则
     */
    private boolean matchRule(Rule rule, Map<String, Object> logData) {
        try {
            // 1. 检查监控对象
            if (!checkMonitorTarget(rule, logData)) {
                return false;
            }

            // 2. 评估规则
            return ruleEngine.evaluate(rule, logData);

        } catch (Exception e) {
            log.error("Error matching rule: {}", rule.getRuleName(), e);
            return false;
        }
    }

    /**
     * 检查监控对象
     */
    private boolean checkMonitorTarget(Rule rule, Map<String, Object> logData) {
        String monitorTarget = rule.getMonitorTarget();
        if (monitorTarget == null || monitorTarget.isEmpty()) {
            return true; // 不限制监控对象
        }

        // 检查模块
        String module = (String) logData.get("module");
        if (module != null && module.contains(monitorTarget)) {
            return true;
        }

        // 检查操作
        String operation = (String) logData.get("operation");
        if (operation != null && operation.contains(monitorTarget)) {
            return true;
        }

        // 检查URL
        String requestUrl = (String) logData.get("requestUrl");
        return requestUrl != null && requestUrl.contains(monitorTarget);
    }

    /**
     * 加载所有启用的规则
     */
    public void loadRules() {
        try {
            TenantContext.setIgnoreTenant(true);
            List<Rule> allRules = ruleMapper.selectAllEnabledRules();
            ruleCache.clear();

            for (Rule rule : allRules) {
                String cacheKey = rule.getTenantId() + ":" + rule.getSystemId();
                ruleCache.computeIfAbsent(cacheKey, k -> new java.util.ArrayList<>()).add(rule);
            }

            log.info("Loaded {} rules from database", allRules.size());
        } catch (Exception e) {
            log.error("Failed to load rules", e);
        }
    }

    /**
     * 刷新规则缓存（定时任务）
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000) // 每分钟刷新一次
    public void refreshRules() {
        loadRules();
    }

    /**
     * 清除指定租户系统的规则缓存
     */
    public void clearCache(String tenantId, String systemId) {
        String cacheKey = tenantId + ":" + systemId;
        ruleCache.remove(cacheKey);
        log.info("Cleared rule cache for: {}", cacheKey);
    }
}