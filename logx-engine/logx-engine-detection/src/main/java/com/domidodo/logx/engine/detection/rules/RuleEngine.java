package com.domidodo.logx.engine.detection.rules;

import com.domidodo.logx.engine.detection.entity.Rule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 规则引擎
 * 负责判断日志是否触发规则
 */
@Slf4j
@Component
public class RuleEngine {

    /**
     * 评估规则是否触发
     *
     * @param rule    规则
     * @param logData 日志数据
     * @return true=触发，false=未触发
     */
    public boolean evaluate(Rule rule, Map<String, Object> logData) {
        try {
            String ruleType = rule.getRuleType();

            return switch (ruleType) {
                case "RESPONSE_TIME" -> evaluateResponseTime(rule, logData);
                case "ERROR_RATE" -> evaluateErrorRate(rule, logData);
                case "CONTINUOUS_FAILURE" -> evaluateContinuousFailure(rule, logData);
                case "FREQUENT_OPERATION" -> evaluateFrequentOperation(rule, logData);
                default -> {
                    log.warn("Unknown rule type: {}", ruleType);
                    yield false;
                }
            };
        } catch (Exception e) {
            log.error("Error evaluating rule: {}", rule.getRuleName(), e);
            return false;
        }
    }

    /**
     * 评估响应时间规则
     */
    private boolean evaluateResponseTime(Rule rule, Map<String, Object> logData) {
        Object responseTimeObj = logData.get("responseTime");
        if (responseTimeObj == null) {
            return false;
        }

        long responseTime = ((Number) responseTimeObj).longValue();
        long threshold = Long.parseLong(rule.getConditionValue());
        String operator = rule.getConditionOperator();

        return compareValue(responseTime, threshold, operator);
    }

    /**
     * 评估错误率规则
     * 注意：这个需要统计数据，简化实现为检查是否为错误日志
     */
    private boolean evaluateErrorRate(Rule rule, Map<String, Object> logData) {
        String level = (String) logData.get("level");
        if (level == null) {
            return false;
        }

        // TODO 简化实现：只要是ERROR级别就认为可能触发
        //  实际应该统计一段时间内的错误率
        return "ERROR".equals(level);
    }

    /**
     * 评估连续失败规则
     * 注意：需要维护状态，这里简化为检查是否失败
     */
    private boolean evaluateContinuousFailure(Rule rule, Map<String, Object> logData) {
        String level = (String) logData.get("level");
        if (level == null) {
            return false;
        }

        //TODO 简化实现：检查是否为错误
        // 实际应该维护连续失败计数
        return "ERROR".equals(level);
    }

    /**
     * 评估频繁操作规则
     * 注意：需要统计数据，这里简化实现
     */
    private boolean evaluateFrequentOperation(Rule rule, Map<String, Object> logData) {
        // TODO 简化实现：始终返回false
        //  实际应该统计用户的操作频率
        return false;
    }

    /**
     * 比较值
     */
    private boolean compareValue(long actualValue, long thresholdValue, String operator) {
        return switch (operator) {
            case ">" -> actualValue > thresholdValue;
            case ">=" -> actualValue >= thresholdValue;
            case "<" -> actualValue < thresholdValue;
            case "<=" -> actualValue <= thresholdValue;
            case "=", "==" -> actualValue == thresholdValue;
            default -> {
                log.warn("Unknown operator: {}", operator);
                yield false;
            }
        };
    }

    /**
     * 生成告警内容
     */
    public String generateAlertContent(Rule rule, Map<String, Object> logData) {
        StringBuilder content = new StringBuilder();

        content.append("规则名称: ").append(rule.getRuleName()).append("\n");
        content.append("规则类型: ").append(rule.getRuleType()).append("\n");
        content.append("监控对象: ").append(rule.getMonitorTarget()).append("\n");
        content.append("触发条件: ").append(rule.getMonitorMetric())
                .append(" ").append(rule.getConditionOperator())
                .append(" ").append(rule.getConditionValue()).append("\n");

        // 添加日志详情
        content.append("\n触发日志详情:\n");
        content.append("时间: ").append(logData.get("timestamp")).append("\n");
        content.append("级别: ").append(logData.get("level")).append("\n");
        content.append("模块: ").append(logData.get("module")).append("\n");
        content.append("消息: ").append(logData.get("message")).append("\n");

        if (logData.containsKey("responseTime")) {
            content.append("响应时间: ").append(logData.get("responseTime")).append("ms\n");
        }

        if (logData.containsKey("userId")) {
            content.append("用户: ").append(logData.get("userName"))
                    .append(" (").append(logData.get("userId")).append(")\n");
        }

        return content.toString();
    }
}