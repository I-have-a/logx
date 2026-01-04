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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.domidodo.logx.common.util.JsonUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdatedRuleExecutor {

    private final RuleMapper ruleMapper;
    private final EnhancedRuleEngine enhancedRuleEngine; // 使用增强版引擎
    private final RuleStateManager stateManager; // 状态管理器
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
        log.info("RuleExecutor已初始化，加载了{}个租户系统规则", ruleCache.size());
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
                    log.error("处理日志消息时出错", e);
                }
            }

            // 5. 手动提交offset
            acknowledgment.acknowledge();

            log.debug("已处理{}条日志，{}条匹配规则", totalCount, matchedCount);

        } catch (Exception e) {
            log.error("处理日志批处理时出错", e);
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

            // 2. 使用增强规则引擎评估
            return enhancedRuleEngine.evaluate(rule, logData);

        } catch (Exception e) {
            log.error("匹配规则时出错：{}", rule.getRuleName(), e);
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

        // 支持多种维度的监控对象
        // 格式：userId:xxx / module:xxx / ip:xxx / operation:xxx / /api/xxx

        // 检查用户ID
        if (monitorTarget.startsWith("userId:")) {
            String targetUserId = monitorTarget.substring(7);
            String userId = (String) logData.get("userId");
            return targetUserId.equals(userId);
        }

        // 检查模块
        if (monitorTarget.startsWith("module:")) {
            String targetModule = monitorTarget.substring(7);
            String module = (String) logData.get("module");
            return module != null && module.contains(targetModule);
        }

        // 检查IP
        if (monitorTarget.startsWith("ip:")) {
            String targetIp = monitorTarget.substring(3);
            String ip = (String) logData.get("ip");
            return targetIp.equals(ip);
        }

        // 检查操作
        if (monitorTarget.startsWith("operation:")) {
            String targetOperation = monitorTarget.substring(10);
            String operation = (String) logData.get("operation");
            return operation != null && operation.contains(targetOperation);
        }

        // 检查URL（传统方式，兼容老代码）
        String requestUrl = (String) logData.get("requestUrl");
        if (requestUrl != null && requestUrl.contains(monitorTarget)) {
            return true;
        }

        // 检查模块（传统方式）
        String module = (String) logData.get("module");
        if (module != null && module.contains(monitorTarget)) {
            return true;
        }

        // 检查操作（传统方式）
        String operation = (String) logData.get("operation");
        return operation != null && operation.contains(monitorTarget);
    }

    /**
     * 加载所有可用的规则
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

            log.info("已从数据库加载{}条规则", allRules.size());
        } catch (Exception e) {
            log.error("未能加载规则", e);
        }
    }

    /**
     * 刷新规则缓存（定时任务）
     */
    @Scheduled(fixedRate = 60000) // 每分钟刷新一次
    public void refreshRules() {
        loadRules();
    }

    /**
     * 清理过期状态（定时任务）
     */
    @Scheduled(fixedRate = 300000) // 每5分钟清理一次
    public void cleanupExpiredStates() {
        try {
            stateManager.cleanupExpiredStates();
            log.debug("已清理过期的规则状态");
        } catch (Exception e) {
            log.error("未能清理过期状态", e);
        }
    }

    /**
     * 清除指定租户系统的规则缓存
     */
    public void clearCache(String tenantId, String systemId) {
        String cacheKey = tenantId + ":" + systemId;
        ruleCache.remove(cacheKey);
        log.info("已清除{}的规则缓存", cacheKey);
    }

    /**
     * 清除指定规则的状态
     */
    public void clearRuleState(Long ruleId) {
        // 清理该规则相关的所有状态
        stateManager.continuousStateMap.keySet().stream()
                .filter(key -> key.startsWith(ruleId + ":"))
                .forEach(stateManager::resetContinuousState);

        stateManager.batchOperationMap.keySet().stream()
                .filter(key -> key.startsWith(ruleId + ":"))
                .forEach(stateManager::resetBatchOperationState);

        log.info("规则的已清除状态：{}", ruleId);
    }
}