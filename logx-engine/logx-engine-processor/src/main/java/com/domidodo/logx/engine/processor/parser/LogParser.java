package com.domidodo.logx.engine.processor.parser;

import com.domidodo.logx.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 日志解析器
 * 负责：
 * 1. JSON 解析
 * 2. 字段标准化
 * 3. 敏感信息脱敏
 * 4. 字段补全
 */
@Slf4j
@Component
public class LogParser {

    /**
     * 手机号正则
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    /**
     * 身份证号正则
     */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{17}[\\dXx]");

    /**
     * 邮箱正则
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.-]+@[\\w.-]+\\.\\w+");

    /**
     * 解析日志
     */
    public Map<String, Object> parse(String logJson) {
        // 1. JSON 解析
        Map<String, Object> logMap = JsonUtil.parseObject(logJson);
        if (logMap == null) {
            throw new IllegalArgumentException("Invalid JSON format");
        }

        // 2. 标准化处理
        Map<String, Object> normalized = normalize(logMap);

        // 3. 敏感信息脱敏
        desensitize(normalized);

        // 4. 字段补全
        fillMissingFields(normalized);

        return normalized;
    }

    /**
     * 标准化字段名称和值
     */
    private Map<String, Object> normalize(Map<String, Object> logMap) {
        Map<String, Object> normalized = new HashMap<>();

        // 核心字段标准化
        normalized.put("traceId", getString(logMap, "traceId", "trace_id"));
        normalized.put("spanId", getString(logMap, "spanId", "span_id"));
        normalized.put("tenantId", getString(logMap, "tenantId", "tenant_id"));
        normalized.put("systemId", getString(logMap, "systemId", "system_id"));
        normalized.put("level", getString(logMap, "level"));
        normalized.put("logger", getString(logMap, "logger"));
        normalized.put("thread", getString(logMap, "thread"));
        normalized.put("className", getString(logMap, "className", "class_name"));
        normalized.put("methodName", getString(logMap, "methodName", "method_name"));
        normalized.put("lineNumber", getInteger(logMap, "lineNumber", "line_number"));
        normalized.put("message", getString(logMap, "message"));
        normalized.put("exception", getString(logMap, "exception", "stackTrace"));

        // 用户信息
        normalized.put("userId", getString(logMap, "userId", "user_id"));
        normalized.put("userName", getString(logMap, "userName", "user_name"));

        // 业务信息
        normalized.put("module", getString(logMap, "module"));
        normalized.put("operation", getString(logMap, "operation"));

        // 请求信息
        normalized.put("requestUrl", getString(logMap, "requestUrl", "request_url"));
        normalized.put("requestMethod", getString(logMap, "requestMethod", "request_method"));
        normalized.put("requestParams", getString(logMap, "requestParams", "request_params"));
        normalized.put("responseTime", getLong(logMap, "responseTime", "response_time"));

        // 网络信息
        normalized.put("ip", getString(logMap, "ip"));
        normalized.put("userAgent", getString(logMap, "userAgent", "user_agent"));

        // 扩展信息
        normalized.put("tags", logMap.get("tags"));
        normalized.put("extra", logMap.get("extra"));

        // 时间戳处理
        normalized.put("timestamp", parseTimestamp(logMap.get("timestamp")));

        return normalized;
    }

    /**
     * 敏感信息脱敏
     */
    private void desensitize(Map<String, Object> logMap) {
        // 脱敏 message
        String message = (String) logMap.get("message");
        if (message != null) {
            message = desensitizeString(message);
            logMap.put("message", message);
        }

        // 脱敏 requestParams
        String requestParams = (String) logMap.get("requestParams");
        if (requestParams != null) {
            requestParams = desensitizeString(requestParams);
            logMap.put("requestParams", requestParams);
        }

        // 脱敏 userName（只显示姓）
        String userName = (String) logMap.get("userName");
        if (userName != null && userName.length() > 1) {
            logMap.put("userName", userName.substring(0, 1) + "**");
        }
    }

    /**
     * 字符串脱敏
     */
    private String desensitizeString(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 手机号脱敏：138****5678
        text = PHONE_PATTERN.matcher(text).replaceAll(match -> {
            String phone = match.group();
            return phone.substring(0, 3) + "****" + phone.substring(7);
        });

        // 身份证脱敏：310***********1234
        text = ID_CARD_PATTERN.matcher(text).replaceAll(match -> {
            String idCard = match.group();
            return idCard.substring(0, 3) + "***********" + idCard.substring(14);
        });

        // 邮箱脱敏：u***@example.com
        text = EMAIL_PATTERN.matcher(text).replaceAll(match -> {
            String email = match.group();
            int atIndex = email.indexOf('@');
            if (atIndex > 1) {
                return email.substring(0, 1) + "***" + email.substring(atIndex);
            }
            return email;
        });

        return text;
    }

    /**
     * 补全缺失字段
     */
    private void fillMissingFields(Map<String, Object> logMap) {
        // 补全时间戳
        if (logMap.get("timestamp") == null) {
            logMap.put("timestamp", LocalDateTime.now());
        }

        // 补全日志级别
        if (logMap.get("level") == null) {
            logMap.put("level", "INFO");
        }

        // 补全响应时间
        if (logMap.get("responseTime") == null) {
            logMap.put("responseTime", 0L);
        }
    }

    /**
     * 解析时间戳
     */
    private LocalDateTime parseTimestamp(Object timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now();
        }

        try {
            if (timestamp instanceof Long) {
                return LocalDateTime.ofInstant(
                        Instant.ofEpochMilli((Long) timestamp),
                        ZoneId.systemDefault()
                );
            } else if (timestamp instanceof String) {
                return LocalDateTime.parse((String) timestamp);
            }
        } catch (Exception e) {
            log.warn("Failed to parse timestamp: {}", timestamp);
        }

        return LocalDateTime.now();
    }

    /**
     * 获取字符串值（支持多个候选字段名）
     */
    private String getString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * 获取整数值
     */
    private Integer getInteger(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                try {
                    return Integer.parseInt((String) value);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    /**
     * 获取长整数值
     */
    private Long getLong(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            } else if (value instanceof String) {
                try {
                    return Long.parseLong((String) value);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }
}
