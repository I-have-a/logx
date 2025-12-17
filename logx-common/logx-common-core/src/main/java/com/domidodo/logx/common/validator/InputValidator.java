package com.domidodo.logx.common.validator;

import com.domidodo.logx.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 输入验证工具类
 * 防止SQL注入、XSS攻击等安全问题
 */
@Slf4j
@Component
public class InputValidator {

    // SQL关键字黑名单
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute|script|javascript|<script|</script>)",
            Pattern.CASE_INSENSITIVE
    );

    // XSS攻击模式
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)(<script|</script>|javascript:|onerror=|onload=|<iframe|</iframe>)",
            Pattern.CASE_INSENSITIVE
    );

    // 租户ID格式
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    // 系统ID格式
    private static final Pattern SYSTEM_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    /**
     * 验证租户ID
     */
    public void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            throw new BusinessException("租户ID不能为空");
        }

        if (tenantId.length() > 64) {
            throw new BusinessException("租户ID长度不能超过64个字符");
        }

        if (!TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            log.warn("Invalid tenant ID format: {}", tenantId);
            throw new BusinessException("租户ID格式不正确");
        }

        checkSqlInjection(tenantId, "租户ID");
    }

    /**
     * 验证系统ID
     */
    public void validateSystemId(String systemId) {
        if (systemId == null || systemId.isEmpty()) {
            throw new BusinessException("系统ID不能为空");
        }

        if (systemId.length() > 64) {
            throw new BusinessException("系统ID长度不能超过64个字符");
        }

        if (!SYSTEM_ID_PATTERN.matcher(systemId).matches()) {
            log.warn("Invalid system ID format: {}", systemId);
            throw new BusinessException("系统ID格式不正确");
        }

        checkSqlInjection(systemId, "系统ID");
    }

    /**
     * 验证日志消息
     */
    public String validateLogMessage(String message) {
        if (message == null) {
            return "";
        }

        // 限制长度
        if (message.length() > 10000) {
            log.warn("Log message too long: {} chars", message.length());
            message = message.substring(0, 10000) + "...[truncated]";
        }

        // XSS防护
        message = sanitizeXss(message);

        return message;
    }

    /**
     * 验证查询关键字
     */
    public String validateQueryKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return "";
        }

        // 限制长度
        if (keyword.length() > 500) {
            throw new BusinessException("查询关键字长度不能超过500个字符");
        }

        // SQL注入检查
        checkSqlInjection(keyword, "查询关键字");

        // XSS防护
        return sanitizeXss(keyword);
    }

    /**
     * 验证排序字段
     */
    public String validateSortField(String sortField) {
        if (sortField == null || sortField.isEmpty()) {
            return "timestamp";
        }

        // 只允许字母、数字和下划线
        if (!sortField.matches("^[a-zA-Z0-9_]{1,64}$")) {
            log.warn("Invalid sort field: {}", sortField);
            throw new BusinessException("排序字段格式不正确");
        }

        // 白名单检查
        String[] allowedFields = {
                "timestamp", "level", "module", "operation",
                "userId", "responseTime", "createTime"
        };

        boolean allowed = false;
        for (String field : allowedFields) {
            if (field.equalsIgnoreCase(sortField)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            throw new BusinessException("不支持的排序字段: " + sortField);
        }

        return sortField;
    }

    /**
     * 验证排序方式
     */
    public String validateSortOrder(String sortOrder) {
        if (sortOrder == null || sortOrder.isEmpty()) {
            return "desc";
        }

        String order = sortOrder.toLowerCase();
        if (!"asc".equals(order) && !"desc".equals(order)) {
            throw new BusinessException("排序方式只能是 asc 或 desc");
        }

        return order;
    }

    /**
     * 验证分页参数
     */
    public void validatePagination(Integer page, Integer size) {
        if (page != null && page < 1) {
            throw new BusinessException("页码必须大于0");
        }

        if (size != null) {
            if (size < 1) {
                throw new BusinessException("每页大小必须大于0");
            }
            if (size > 1000) {
                throw new BusinessException("每页大小不能超过1000");
            }
        }
    }

    /**
     * SQL注入检查
     */
    private void checkSqlInjection(String input, String fieldName) {
        if (SQL_INJECTION_PATTERN.matcher(input).find()) {
            log.warn("SQL injection attempt detected in {}: {}", fieldName, input);
            throw new BusinessException(fieldName + "包含非法字符");
        }
    }

    /**
     * XSS防护 - 清理危险的HTML/JS内容
     */
    private String sanitizeXss(String input) {
        if (input == null) {
            return null;
        }

        // 移除脚本标签
        String sanitized = input;

        // 替换常见的XSS攻击向量
        sanitized = sanitized.replaceAll("(?i)<script", "&lt;script");
        sanitized = sanitized.replaceAll("(?i)</script>", "&lt;/script&gt;");
        sanitized = sanitized.replaceAll("(?i)javascript:", "");
        sanitized = sanitized.replaceAll("(?i)onerror\\s*=", "");
        sanitized = sanitized.replaceAll("(?i)onload\\s*=", "");

        return sanitized;
    }

    /**
     * 验证时间范围
     */
    public void validateTimeRange(java.time.LocalDateTime startTime,
                                  java.time.LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BusinessException("开始时间和结束时间不能为空");
        }

        if (startTime.isAfter(endTime)) {
            throw new BusinessException("开始时间不能晚于结束时间");
        }

        // 限制查询时间范围（防止查询过大数据量）
        java.time.Duration duration = java.time.Duration.between(startTime, endTime);
        if (duration.toDays() > 90) {
            throw new BusinessException("查询时间范围不能超过90天");
        }
    }

    /**
     * 验证响应时间范围
     */
    public void validateResponseTimeRange(Long minTime, Long maxTime) {
        if (minTime != null && minTime < 0) {
            throw new BusinessException("最小响应时间不能为负数");
        }

        if (maxTime != null && maxTime < 0) {
            throw new BusinessException("最大响应时间不能为负数");
        }

        if (minTime != null && maxTime != null && minTime > maxTime) {
            throw new BusinessException("最小响应时间不能大于最大响应时间");
        }
    }
}