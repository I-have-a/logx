package com.domidodo.logx.engine.storage.model;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import com.alibaba.excel.annotation.write.style.HeadStyle;
import com.alibaba.excel.enums.poi.FillPatternTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 日志 Excel 导出实体
 * 使用 EasyExcel 注解定义 Excel 列
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@HeadRowHeight(25)
@ContentRowHeight(20)
@HeadStyle(fillPatternType = FillPatternTypeEnum.SOLID_FOREGROUND, fillForegroundColor = 22)
public class LogExcelDTO {

    @ExcelProperty(value = "日志ID", index = 0)
    @ColumnWidth(20)
    private String id;

    @ExcelProperty(value = "追踪ID", index = 1)
    @ColumnWidth(20)
    private String traceId;

    @ExcelProperty(value = "租户ID", index = 2)
    @ColumnWidth(12)
    private String tenantId;

    @ExcelProperty(value = "系统ID", index = 3)
    @ColumnWidth(12)
    private String systemId;

    @ExcelProperty(value = "时间", index = 4)
    @ColumnWidth(20)
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private Date timestamp;

    @ExcelProperty(value = "级别", index = 5)
    @ColumnWidth(8)
    private String level;

    @ExcelProperty(value = "模块", index = 6)
    @ColumnWidth(15)
    private String module;

    @ExcelProperty(value = "操作", index = 7)
    @ColumnWidth(20)
    private String operation;

    @ExcelProperty(value = "类名", index = 8)
    @ColumnWidth(35)
    private String className;

    @ExcelProperty(value = "方法名", index = 9)
    @ColumnWidth(18)
    private String methodName;

    @ExcelProperty(value = "行号", index = 10)
    @ColumnWidth(8)
    private Integer lineNumber;

    @ExcelProperty(value = "日志消息", index = 11)
    @ColumnWidth(50)
    private String message;

    @ExcelProperty(value = "异常信息", index = 12)
    @ColumnWidth(40)
    private String exception;

    @ExcelProperty(value = "用户ID", index = 13)
    @ColumnWidth(12)
    private String userId;

    @ExcelProperty(value = "用户名", index = 14)
    @ColumnWidth(12)
    private String userName;

    @ExcelProperty(value = "请求URL", index = 15)
    @ColumnWidth(35)
    private String requestUrl;

    @ExcelProperty(value = "请求方法", index = 16)
    @ColumnWidth(10)
    private String requestMethod;

    @ExcelProperty(value = "响应时间(ms)", index = 17)
    @ColumnWidth(12)
    private Long responseTime;

    @ExcelProperty(value = "IP地址", index = 18)
    @ColumnWidth(15)
    private String ip;

    @ExcelProperty(value = "线程名", index = 19)
    @ColumnWidth(18)
    private String thread;

    @ExcelProperty(value = "标签", index = 20)
    @ColumnWidth(20)
    private String tags;

    @ExcelIgnore
    private Map<String, Object> extra;

    /**
     * 从 ES 文档 Map 转换为 Excel DTO
     */
    public static LogExcelDTO fromMap(Map<String, Object> document) {
        LogExcelDTO dto = new LogExcelDTO();

        dto.setId(getString(document, "_id"));
        dto.setTraceId(getString(document, "traceId"));
        dto.setTenantId(getString(document, "tenantId"));
        dto.setSystemId(getString(document, "systemId"));
        dto.setTimestamp(getDate(document, "timestamp"));
        dto.setLevel(getString(document, "level"));
        dto.setModule(getString(document, "module"));
        dto.setOperation(getString(document, "operation"));
        dto.setClassName(getString(document, "className"));
        dto.setMethodName(getString(document, "methodName"));
        dto.setLineNumber(getInteger(document, "lineNumber"));
        dto.setMessage(truncate(getString(document, "message"), 500));
        dto.setException(truncate(getString(document, "exception"), 500));
        dto.setUserId(getString(document, "userId"));
        dto.setUserName(getString(document, "userName"));
        dto.setRequestUrl(getString(document, "requestUrl"));
        dto.setRequestMethod(getString(document, "requestMethod"));
        dto.setResponseTime(getLong(document, "responseTime"));
        dto.setIp(getString(document, "ip"));
        dto.setThread(getString(document, "thread"));
        dto.setTags(getTagsString(document, "tags"));

        // 存储额外字段
        if (document.containsKey("extra")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> extraMap = (Map<String, Object>) document.get("extra");
            dto.setExtra(extraMap);
        }

        return dto;
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private static Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Date getDate(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;

        if (value instanceof Date) {
            return (Date) value;
        }

        if (value instanceof Long) {
            return Date.from(Instant.ofEpochMilli((Long) value));
        }

        if (value instanceof Number) {
            return Date.from(Instant.ofEpochMilli(((Number) value).longValue()));
        }

        if (value instanceof String str) {
            try {
                if (str.contains("T")) {
                    LocalDateTime ldt = LocalDateTime.parse(str, DateTimeFormatter.ISO_DATE_TIME);
                    return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
                }
                LocalDateTime ldt = LocalDateTime.parse(str, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    private static String getTagsString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;

        if (value instanceof List<?> list) {
            return String.join(", ", list.stream()
                    .map(Object::toString)
                    .toArray(String[]::new));
        }

        return value.toString();
    }

    private static String truncate(String str, int maxLength) {
        if (str == null) return null;
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }
}