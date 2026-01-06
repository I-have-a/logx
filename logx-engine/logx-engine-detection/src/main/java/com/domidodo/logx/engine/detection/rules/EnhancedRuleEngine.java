package com.domidodo.logx.engine.detection.rules;

import com.domidodo.logx.engine.detection.entity.Rule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增强规则引擎
 * 支持：字段值比较、批量操作监控、连续请求监控
 * 支持多级字段路径访问，如：one.two[0].three
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnhancedRuleEngine {

    private final RuleStateManager stateManager;

    /**
     * 数组索引模式：匹配 fieldName[index] 格式
     */
    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("^(.+?)\\[(\\d+)]$");

    /**
     * 评估规则是否触发
     */
    public boolean evaluate(Rule rule, Map<String, Object> logData) {
        try {
            String ruleType = rule.getRuleType();

            return switch (ruleType) {
                // 原有规则类型
                case "RESPONSE_TIME" -> evaluateResponseTime(rule, logData);
                case "ERROR_RATE" -> evaluateErrorRate(rule, logData);

                // 新增核心规则类型
                case "FIELD_COMPARE" -> evaluateFieldCompare(rule, logData);
                case "BATCH_OPERATION" -> evaluateBatchOperation(rule, logData);
                case "CONTINUOUS_REQUEST" -> evaluateContinuousRequest(rule, logData);

                default -> {
                    log.warn("未知规则类型：{}", ruleType);
                    yield false;
                }
            };
        } catch (Exception e) {
            log.error("评估规则时出错：{}", rule.getRuleName(), e);
            return false;
        }
    }

    /**
     * 1. 字段值比较规则
     * 支持对日志中任意字段进行比较，包括多级嵌套字段
     * <p>
     * 配置示例：
     * - monitorMetric: "responseTime" / "user.profile.age" / "data[0].name" / "one.two[0].three"
     * - conditionOperator: ">", "<", ">=", "<=", "=", "!=", "contains", "startsWith"
     * - conditionValue: 具体的值
     */
    private boolean evaluateFieldCompare(Rule rule, Map<String, Object> logData) {
        String fieldPath = rule.getMonitorMetric();
        String operator = rule.getConditionOperator();
        String expectedValue = rule.getConditionValue();

        // 使用多级字段路径解析获取值
        Object actualValue = getNestedValue(logData, fieldPath);
        if (actualValue == null) {
            // 字段不存在或值为null，跳过此规则
            log.debug("字段路径不存在或值为null，跳过规则：字段路径={}", fieldPath);
            return false;
        }

        try {
            // 数字比较
            if (actualValue instanceof Number) {
                long actual = ((Number) actualValue).longValue();
                long expected = Long.parseLong(expectedValue);
                return compareNumber(actual, expected, operator);
            }

            // 字符串比较
            String actualStr = actualValue.toString();
            return compareString(actualStr, expectedValue, operator);

        } catch (Exception e) {
            log.error("字段比较错误：字段路径={}，运算符={}、值={}",
                    fieldPath, operator, expectedValue, e);
            return false;
        }
    }

    /**
     * 获取多级嵌套字段的值
     * 支持格式：
     * - 简单字段：fieldName
     * - 嵌套对象：one.two.three
     * - 数组访问：data[0]
     * - 混合路径：one.two[0].three.four[1].name
     *
     * @param data      数据对象（Map）
     * @param fieldPath 字段路径
     * @return 字段值，如果路径无效或字段不存在则返回 null
     */
    public Object getNestedValue(Map<String, Object> data, String fieldPath) {
        if (data == null || fieldPath == null || fieldPath.isEmpty()) {
            return null;
        }

        // 按点号分割路径
        String[] pathParts = fieldPath.split("\\.");
        Object current = data;

        for (String part : pathParts) {
            if (current == null) {
                return null;
            }

            // 检查是否包含数组索引，如：items[0] 或 data[2]
            Matcher matcher = ARRAY_INDEX_PATTERN.matcher(part);

            if (matcher.matches()) {
                // 带数组索引的字段
                String fieldName = matcher.group(1);
                int arrayIndex = Integer.parseInt(matcher.group(2));

                // 先获取字段值
                current = getFieldValue(current, fieldName);
                if (current == null) {
                    return null;
                }

                // 再获取数组元素
                current = getArrayElement(current, arrayIndex);
            } else {
                // 普通字段
                current = getFieldValue(current, part);
            }
        }

        return current;
    }

    /**
     * 从对象中获取字段值
     *
     * @param obj       对象（支持 Map）
     * @param fieldName 字段名
     * @return 字段值，不存在则返回 null
     */
    @SuppressWarnings("unchecked")
    private Object getFieldValue(Object obj, String fieldName) {
        if (obj == null || fieldName == null || fieldName.isEmpty()) {
            return null;
        }

        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            return map.get(fieldName);
        }

        // 如果需要支持 POJO 对象，可以在这里添加反射逻辑
        log.debug("不支持的对象类型：{}，无法获取字段：{}", obj.getClass().getName(), fieldName);
        return null;
    }

    /**
     * 从数组/列表中获取指定索引的元素
     *
     * @param obj   数组或列表对象
     * @param index 索引
     * @return 元素值，索引越界或类型不支持则返回 null
     */
    private Object getArrayElement(Object obj, int index) {
        if (obj == null || index < 0) {
            return null;
        }

        try {
            if (obj instanceof List<?> list) {
                if (index >= list.size()) {
                    log.debug("列表索引越界：size={}，index={}", list.size(), index);
                    return null;
                }
                return list.get(index);
            }

            if (obj.getClass().isArray()) {
                Object[] array = (Object[]) obj;
                if (index >= array.length) {
                    log.debug("数组索引越界：length={}，index={}", array.length, index);
                    return null;
                }
                return array[index];
            }

            log.debug("不支持的类型用于数组访问：{}", obj.getClass().getName());
            return null;

        } catch (ClassCastException e) {
            log.debug("数组类型转换失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查字段路径是否存在
     *
     * @param data      数据对象
     * @param fieldPath 字段路径
     * @return true 如果路径存在（值可以为 null）
     */
    public boolean hasNestedField(Map<String, Object> data, String fieldPath) {
        if (data == null || fieldPath == null || fieldPath.isEmpty()) {
            return false;
        }

        String[] pathParts = fieldPath.split("\\.");
        Object current = data;

        for (int i = 0; i < pathParts.length; i++) {
            if (current == null) {
                return false;
            }

            String part = pathParts[i];
            Matcher matcher = ARRAY_INDEX_PATTERN.matcher(part);

            if (matcher.matches()) {
                String fieldName = matcher.group(1);
                int arrayIndex = Integer.parseInt(matcher.group(2));

                // 检查字段是否存在
                if (!containsField(current, fieldName)) {
                    return false;
                }

                current = getFieldValue(current, fieldName);
                if (current == null) {
                    return i == pathParts.length - 1; // 最后一个节点值为null也算存在
                }

                // 检查数组索引是否有效
                if (!isValidArrayIndex(current, arrayIndex)) {
                    return false;
                }

                current = getArrayElement(current, arrayIndex);
            } else {
                if (!containsField(current, part)) {
                    return false;
                }
                current = getFieldValue(current, part);
            }
        }

        return true;
    }

    /**
     * 检查对象是否包含指定字段
     */
    @SuppressWarnings("unchecked")
    private boolean containsField(Object obj, String fieldName) {
        if (obj instanceof Map) {
            return ((Map<String, Object>) obj).containsKey(fieldName);
        }
        return false;
    }

    /**
     * 检查数组索引是否有效
     */
    private boolean isValidArrayIndex(Object obj, int index) {
        if (index < 0) {
            return false;
        }
        if (obj instanceof List) {
            return index < ((List<?>) obj).size();
        }
        if (obj.getClass().isArray()) {
            return index < ((Object[]) obj).length;
        }
        return false;
    }

    /**
     * 2. 批量操作监控规则
     * 监控时间窗口内的操作次数
     * <p>
     * 配置示例：
     * - monitorTarget: "userId:12345" / "module:订单管理" / "ip:192.168.1.1"
     * - monitorMetric: "operationCount"
     * - conditionOperator: ">"
     * - conditionValue: "100:300" (100次/300秒时间窗口)
     */
    private boolean evaluateBatchOperation(Rule rule, Map<String, Object> logData) {
        String target = rule.getMonitorTarget();
        String conditionValue = rule.getConditionValue();
        String operator = rule.getConditionOperator();

        try {
            // 解析条件值：次数:时间窗口(秒)
            String[] parts = conditionValue.split(":");
            int threshold = Integer.parseInt(parts[0]);
            int windowSeconds = parts.length > 1 ? Integer.parseInt(parts[1]) : 60; // 默认60秒

            // 构建状态key（需要包含维度信息）
            String stateKey = buildBatchOperationKey(rule, logData, target);

            // 记录本次操作并获取时间窗口内的总次数
            int operationCount = stateManager.recordBatchOperation(stateKey, windowSeconds);

            // 比较操作次数
            return compareNumber(operationCount, threshold, operator);

        } catch (Exception e) {
            log.error("批处理操作评估错误：规则={}，目标={}",
                    rule.getRuleName(), target, e);
            return false;
        }
    }

    /**
     * 3. 连续请求监控规则
     * 监控连续成功/失败的次数
     * <p>
     * 配置示例：
     * - monitorTarget: "/api/order/create" / "module:订单管理"
     * - monitorMetric: "continuousFailure" / "continuousSuccess"
     * - conditionOperator: ">"
     * - conditionValue: "5" (连续5次)
     */
    private boolean evaluateContinuousRequest(Rule rule, Map<String, Object> logData) {
        String target = rule.getMonitorTarget();
        String metric = rule.getMonitorMetric();
        String operator = rule.getConditionOperator();
        int threshold = Integer.parseInt(rule.getConditionValue());

        try {
            // 构建状态key
            String stateKey = buildContinuousRequestKey(rule, logData, target);

            // 判断本次请求是否失败
            boolean isFailed = isRequestFailed(logData, metric);

            // 记录连续状态并获取计数
            int continuousCount = stateManager.recordContinuousFailure(stateKey, isFailed);

            // 只在失败时才触发告警判断
            if (isFailed) {
                return compareNumber(continuousCount, threshold, operator);
            }

            return false;

        } catch (Exception e) {
            log.error("连续请求评估错误：规则={}，目标={}",
                    rule.getRuleName(), target, e);
            return false;
        }
    }

    /**
     * 响应时间规则（支持多级路径）
     */
    private boolean evaluateResponseTime(Rule rule, Map<String, Object> logData) {
        // 默认使用 responseTime，但也支持配置其他路径
        String fieldPath = rule.getMonitorMetric();
        if (fieldPath == null || fieldPath.isEmpty()) {
            fieldPath = "responseTime";
        }

        Object responseTimeObj = getNestedValue(logData, fieldPath);
        if (responseTimeObj == null) {
            return false;
        }

        long responseTime = ((Number) responseTimeObj).longValue();
        long threshold = Long.parseLong(rule.getConditionValue());
        String operator = rule.getConditionOperator();

        return compareNumber(responseTime, threshold, operator);
    }

    /**
     * 错误率规则
     */
    private boolean evaluateErrorRate(Rule rule, Map<String, Object> logData) {
        String level = (String) logData.get("level");
        return "ERROR".equals(level);
    }

    // ==================== 辅助方法 ====================

    /**
     * 数字比较
     */
    private boolean compareNumber(long actual, long expected, String operator) {
        return switch (operator) {
            case ">" -> actual > expected;
            case ">=" -> actual >= expected;
            case "<" -> actual < expected;
            case "<=" -> actual <= expected;
            case "=", "==" -> actual == expected;
            case "!=" -> actual != expected;
            default -> {
                log.warn("未知数字运算符：{}", operator);
                yield false;
            }
        };
    }

    /**
     * 字符串比较
     */
    private boolean compareString(String actual, String expected, String operator) {
        return switch (operator) {
            case "=" -> actual.equals(expected);
            case "!=" -> !actual.equals(expected);
            case "contains" -> actual.contains(expected);
            case "startsWith" -> actual.startsWith(expected);
            case "endsWith" -> actual.endsWith(expected);
            case "matches" -> actual.matches(expected); // 正则匹配
            default -> {
                log.warn("未知字符串运算符：{}", operator);
                yield false;
            }
        };
    }

    /**
     * 构建批量操作的状态key（支持多级路径）
     */
    private String buildBatchOperationKey(Rule rule, Map<String, Object> logData, String target) {
        StringBuilder key = new StringBuilder();
        key.append(rule.getId()).append(":");
        key.append(rule.getTenantId()).append(":");
        key.append(rule.getSystemId()).append(":");

        // 根据target提取维度值，支持多级路径
        if (target.startsWith("userId:")) {
            String fieldPath = target.substring(7);
            Object value = fieldPath.contains(".") || fieldPath.contains("[")
                    ? getNestedValue(logData, fieldPath)
                    : logData.get("userId");
            key.append("user:").append(value);
        } else if (target.startsWith("module:")) {
            String fieldPath = target.substring(7);
            Object value = fieldPath.contains(".") || fieldPath.contains("[")
                    ? getNestedValue(logData, fieldPath)
                    : logData.get("module");
            key.append("module:").append(value);
        } else if (target.startsWith("ip:")) {
            String fieldPath = target.substring(3);
            Object value = fieldPath.contains(".") || fieldPath.contains("[")
                    ? getNestedValue(logData, fieldPath)
                    : logData.get("ip");
            key.append("ip:").append(value);
        } else if (target.startsWith("operation:")) {
            String fieldPath = target.substring(10);
            Object value = fieldPath.contains(".") || fieldPath.contains("[")
                    ? getNestedValue(logData, fieldPath)
                    : logData.get("operation");
            key.append("operation:").append(value);
        } else if (target.startsWith("field:")) {
            // 新增：支持任意字段路径，如 field:user.profile.id
            String fieldPath = target.substring(6);
            Object value = getNestedValue(logData, fieldPath);
            key.append("field:").append(value);
        } else {
            // 默认使用完整target
            key.append(target);
        }

        return key.toString();
    }

    /**
     * 构建连续请求的状态key
     */
    private String buildContinuousRequestKey(Rule rule, Map<String, Object> logData, String target) {
        StringBuilder key = new StringBuilder();
        key.append(rule.getId()).append(":");
        key.append(rule.getTenantId()).append(":");
        key.append(rule.getSystemId()).append(":");

        // 根据target提取维度
        if (target.contains("/api/")) {
            // URL维度
            key.append("url:").append(logData.get("requestUrl"));
        } else if (target.startsWith("module:")) {
            // 模块维度
            key.append("module:").append(logData.get("module"));
        } else if (target.startsWith("userId:")) {
            // 用户维度
            key.append("user:").append(logData.get("userId"));
        } else if (target.startsWith("field:")) {
            // 新增：支持任意字段路径
            String fieldPath = target.substring(6);
            Object value = getNestedValue(logData, fieldPath);
            key.append("field:").append(value);
        } else {
            key.append(target);
        }

        return key.toString();
    }

    /**
     * 判断请求是否失败
     */
    private boolean isRequestFailed(Map<String, Object> logData, String metric) {
        // 根据metric配置判断失败条件
        if ("continuousFailure".equals(metric)) {
            // 检查level是否为ERROR，或者响应码是否为5xx
            String level = (String) logData.get("level");
            if ("ERROR".equals(level)) {
                return true;
            }

            Object statusCode = logData.get("statusCode");
            if (statusCode instanceof Number) {
                int code = ((Number) statusCode).intValue();
                return code >= 500;
            }
        }

        return false;
    }

    /**
     * 生成告警内容
     */
    public String generateAlertContent(Rule rule, Map<String, Object> logData) {
        StringBuilder content = new StringBuilder();

        content.append("规则名称: ").append(rule.getRuleName()).append("\n");
        content.append("规则类型: ").append(getRuleTypeDesc(rule.getRuleType())).append("\n");
        content.append("监控对象: ").append(rule.getMonitorTarget()).append("\n");
        content.append("监控字段: ").append(rule.getMonitorMetric()).append("\n");
        content.append("触发条件: ").append(rule.getMonitorMetric())
                .append(" ").append(rule.getConditionOperator())
                .append(" ").append(rule.getConditionValue()).append("\n");

        // 显示实际匹配的值
        String fieldPath = rule.getMonitorMetric();
        Object actualValue = getNestedValue(logData, fieldPath);
        if (actualValue != null) {
            content.append("实际值: ").append(actualValue).append("\n");
        }

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

    /**
     * 获取规则类型描述
     */
    private String getRuleTypeDesc(String ruleType) {
        return switch (ruleType) {
            case "FIELD_COMPARE" -> "字段值比较";
            case "BATCH_OPERATION" -> "批量操作监控";
            case "CONTINUOUS_REQUEST" -> "连续请求监控";
            case "RESPONSE_TIME" -> "响应时间监控";
            case "ERROR_RATE" -> "错误率监控";
            default -> ruleType;
        };
    }
}