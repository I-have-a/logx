package com.domidodo.logx.engine.processor.parser;

import com.domidodo.logx.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
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
     * 手机号正则（中国）
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /**
     * 身份证号正则（中国）
     */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile(
            "(?<!\\d)\\d{17}[\\dXx](?!\\d)");

    /**
     * 邮箱正则
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[\\w.%-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");

    /**
     * 银行卡号正则（13-19位数字）
     */
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile(
            "(?<!\\d)\\d{13,19}(?!\\d)");

    /**
     * IP地址正则
     */
    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    /**
     * 时间戳格式列表
     */
    private static final List<DateTimeFormatter> TIMESTAMP_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    );

    /**
     * 解析日志
     */
    public Map<String, Object> parse(String logJson) {
        // 1. JSON 解析
        Map<String, Object> logMap = JsonUtil.parseObject(logJson);
        if (logMap == null || logMap.isEmpty()) {
            throw new IllegalArgumentException("JSON格式无效或日志为空");
        }

        // 2. 标准化处理
        Map<String, Object> normalized = normalize(logMap);

        // 3. 敏感信息脱敏
        desensitizeEnhanced(normalized);

        // 4. 字段补全
        fillMissingFields(normalized);

        // 5. 字段验证
        validateFields(normalized);

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
        normalized.put("level", normalizeLevel(getString(logMap, "level")));
        normalized.put("logger", getString(logMap, "logger"));
        normalized.put("thread", getString(logMap, "thread"));
        normalized.put("className", getString(logMap, "className", "class_name"));
        normalized.put("methodName", getString(logMap, "methodName", "method_name"));
        normalized.put("lineNumber", getInteger(logMap, "lineNumber", "line_number"));
        normalized.put("message", getString(logMap, "message"));
        normalized.put("exception", getString(logMap, "exception", "stackTrace", "stack_trace"));

        // 用户信息
        normalized.put("userId", getString(logMap, "userId", "user_id"));
        normalized.put("userName", getString(logMap, "userName", "user_name"));

        // 业务信息
        normalized.put("module", getString(logMap, "module"));
        normalized.put("operation", getString(logMap, "operation"));

        // 请求信息
        normalized.put("requestUrl", getString(logMap, "requestUrl", "request_url", "url"));
        normalized.put("requestMethod", getString(logMap, "requestMethod", "request_method", "method"));
        normalized.put("requestParams", getString(logMap, "requestParams", "request_params", "params"));
        normalized.put("responseTime", getLong(logMap, "responseTime", "response_time", "duration"));

        // 网络信息
        normalized.put("ip", getString(logMap, "ip", "clientIp", "client_ip"));
        normalized.put("userAgent", getString(logMap, "userAgent", "user_agent"));

        // 扩展信息
        normalized.put("tags", logMap.get("tags"));
        normalized.put("extra", sanitizeExtra(logMap.get("extra")));

        // 时间戳处理
        normalized.put("timestamp", parseTimestamp(logMap.get("timestamp")));

        return normalized;
    }

    /**
     * 标准化日志级别
     */
    private String normalizeLevel(String level) {
        if (level == null) return "INFO";

        level = level.toUpperCase().trim();

        // 标准化常见变体
        return switch (level) {
            case "WARN", "WARNING" -> "WARN";
            case "ERR", "ERROR", "SEVERE" -> "ERROR";
            case "FATAL", "CRITICAL" -> "FATAL";
            case "TRACE", "FINEST" -> "TRACE";
            case "DEBUG", "FINE" -> "DEBUG";
            default -> "INFO";
        };
    }

    /**
     * 敏感信息脱敏
     */
    private void desensitizeEnhanced(Map<String, Object> logMap) {
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

        // 脱敏 exception
        String exception = (String) logMap.get("exception");
        if (exception != null) {
            exception = desensitizeString(exception);
            logMap.put("exception", exception);
        }

        // 脱敏 userName（只显示姓）
        String userName = (String) logMap.get("userName");
        if (userName != null && userName.length() > 1) {
            logMap.put("userName", userName.charAt(0) + "**");
        }

        // 脱敏 IP地址（保留前两段）
//        String ip = (String) logMap.get("ip");
//        if (ip != null && ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
//            String[] parts = ip.split("\\.");
//            if (parts.length == 4) {
//                logMap.put("ip", parts[0] + "." + parts[1] + ".*.*");
//            }
//        }
    }

    /**
     * 字符串脱敏
     */
    private String desensitizeString(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 使用StringBuilder提高性能
        StringBuilder result = new StringBuilder(text.length());

        // 手机号脱敏：138****5678
        Matcher phoneMatcher = PHONE_PATTERN.matcher(text);
        while (phoneMatcher.find()) {
            String phone = phoneMatcher.group();
            String masked = phone.substring(0, 3) + "****" + phone.substring(7);
            phoneMatcher.appendReplacement(result, masked);
        }
        phoneMatcher.appendTail(result);
        text = result.toString();
        result.setLength(0);

        // 身份证脱敏：310***********1234
        Matcher idCardMatcher = ID_CARD_PATTERN.matcher(text);
        while (idCardMatcher.find()) {
            String idCard = idCardMatcher.group();
            String masked = idCard.substring(0, 3) + "***********" + idCard.substring(14);
            idCardMatcher.appendReplacement(result, masked);
        }
        idCardMatcher.appendTail(result);
        text = result.toString();
        result.setLength(0);

        // 邮箱脱敏：u***@example.com
        Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
        while (emailMatcher.find()) {
            String email = emailMatcher.group();
            int atIndex = email.indexOf('@');
            if (atIndex > 1) {
                String masked = email.charAt(0) + "***" + email.substring(atIndex);
                emailMatcher.appendReplacement(result, masked);
            }
        }
        emailMatcher.appendTail(result);
        text = result.toString();
        result.setLength(0);

        // 银行卡脱敏：6222 **** **** 1234
        Matcher bankCardMatcher = BANK_CARD_PATTERN.matcher(text);
        while (bankCardMatcher.find()) {
            String card = bankCardMatcher.group();
            if (card.length() >= 12) {
                String masked = card.substring(0, 4) + " **** **** " + card.substring(card.length() - 4);
                bankCardMatcher.appendReplacement(result, Matcher.quoteReplacement(masked));
            }
        }
        bankCardMatcher.appendTail(result);
        text = result.toString();

        // IP地址脱敏：192.168.*.*
        Matcher ipMatcher = IP_PATTERN.matcher(text);
        while (ipMatcher.find()) {
            String ip = ipMatcher.group();
            String masked = ip.replaceAll("\\d+", "*");
            ipMatcher.appendReplacement(result, Matcher.quoteReplacement(masked));
        }
        ipMatcher.appendTail(result);
        text = result.toString();

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
        logMap.putIfAbsent("level", "INFO");

        // 补全响应时间
        logMap.putIfAbsent("responseTime", 0L);

        // 补全租户ID
        logMap.putIfAbsent("tenantId", "default");

        // 补全系统ID
        logMap.putIfAbsent("systemId", "unknown");
    }

    /**
     * 解析时间戳（支持多种格式）
     */
    private LocalDateTime parseTimestamp(Object timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now();
        }

        try {
            // 1. 处理Long类型（毫秒时间戳）
            if (timestamp instanceof Long) {
                return LocalDateTime.ofInstant(
                        Instant.ofEpochMilli((Long) timestamp),
                        ZoneId.systemDefault()
                );
            }

            // 2. 处理Integer类型（秒时间戳）
            if (timestamp instanceof Integer) {
                return LocalDateTime.ofInstant(
                        Instant.ofEpochSecond((Integer) timestamp),
                        ZoneId.systemDefault()
                );
            }

            // 3. 处理LocalDateTime类型
            if (timestamp instanceof LocalDateTime) {
                return (LocalDateTime) timestamp;
            }

            // 4. 处理String类型
            if (timestamp instanceof String timeStr) {

                // 4.1 尝试解析为毫秒时间戳
                try {
                    long millis = Long.parseLong(timeStr);
                    // 如果是秒级时间戳（10位数字）
                    if (timeStr.length() == 10) {
                        return LocalDateTime.ofInstant(
                                Instant.ofEpochSecond(millis),
                                ZoneId.systemDefault()
                        );
                    }
                    // 毫秒级时间戳（13位数字）
                    return LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(millis),
                            ZoneId.systemDefault()
                    );
                } catch (NumberFormatException e) {
                    // 不是数字，继续尝试其他格式
                }

                // 4.2 尝试多种日期格式
                for (DateTimeFormatter formatter : TIMESTAMP_FORMATTERS) {
                    try {
                        return LocalDateTime.parse(timeStr, formatter);
                    } catch (DateTimeParseException ignored) {
                        // 尝试下一个格式
                    }
                }

                // 4.3 尝试带时区的格式
                try {
                    ZonedDateTime zdt = ZonedDateTime.parse(timeStr);
                    return zdt.toLocalDateTime();
                } catch (DateTimeParseException ignored) {
                    // 继续
                }
            }

        } catch (Exception e) {
            log.warn("解析时间戳失败：{}，错误：{}", timestamp, e.getMessage());
        }

        // 所有解析都失败，返回当前时间
        log.warn("使用当前时间作为不可解析的时间戳：{}", timestamp);
        return LocalDateTime.now();
    }

    /**
     * 清理extra字段，移除敏感信息
     */
    private Object sanitizeExtra(Object extra) {
        if (extra == null) {
            return null;
        }

        if (extra instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> extraMap = (Map<String, Object>) extra;
            Map<String, Object> sanitized = new HashMap<>();

            // 移除敏感字段
            Set<String> sensitiveKeys = Set.of(
                    "password", "pwd", "token", "secret", "key",
                    "authorization", "auth", "apiKey", "api_key"
            );

            for (Map.Entry<String, Object> entry : extraMap.entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (!sensitiveKeys.contains(key)) {
                    sanitized.put(entry.getKey(), entry.getValue());
                } else {
                    sanitized.put(entry.getKey(), "***");
                }
            }

            return sanitized;
        }

        return extra;
    }

    /**
     * 字段验证
     */
    private void validateFields(Map<String, Object> logMap) {
        // 验证必填字段
        if (logMap.get("tenantId") == null) {
            throw new IllegalArgumentException("tenantId是必需的");
        }

        if (logMap.get("systemId") == null) {
            throw new IllegalArgumentException("systemId是必需的");
        }

        // 验证日志级别
        String level = (String) logMap.get("level");
        Set<String> validLevels = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");
        if (!validLevels.contains(level)) {
            log.warn("日志级别无效：{}，使用INFO", level);
            logMap.put("level", "INFO");
        }
    }

    /**
     * 获取字符串值（支持多个候选字段名）
     */
    private String getString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value.toString().trim();
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
                    return Integer.parseInt(((String) value).trim());
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
                    return Long.parseLong(((String) value).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }
}