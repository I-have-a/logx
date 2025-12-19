package com.domidodo.logx.sdk.core.model;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 日志实体
 * 字段定义与 gRPC Proto 保持一致
 * 使用 google.protobuf.Struct 支持任意 JSON 结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    // ============ 内部字段 ============
    /**
     * 日志ID（内部生成，不发送到服务端）
     */
    private String id;

    /**
     * 系统名称（配置项，用于标识）
     */
    private String systemName;

    // ============ 追踪信息 ============
    /**
     * 追踪ID（分布式追踪）
     */
    private String traceId;

    /**
     * Span ID（调用链）
     */
    private String spanId;

    // ============ 租户信息 ============
    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 系统ID
     */
    private String systemId;

    // ============ 时间信息 ============
    /**
     * 时间戳（LocalDateTime，发送时转为 long）
     */
    private LocalDateTime timestamp;

    // ============ 日志基础信息 ============
    /**
     * 日志级别：DEBUG/INFO/WARN/ERROR
     */
    private String level;

    /**
     * Logger 名称
     */
    private String logger;

    /**
     * 线程名
     */
    private String thread;

    // ============ 代码位置 ============
    /**
     * 类名
     */
    private String className;

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 行号
     */
    private Integer lineNumber;

    // ============ 日志内容 ============
    /**
     * 日志消息
     */
    private String message;

    /**
     * 异常堆栈（完整的异常信息）
     */
    private String exception;

    // ============ 用户信息 ============
    /**
     * 操作用户ID
     */
    private String userId;

    /**
     * 操作用户名
     */
    private String userName;

    // ============ 业务信息 ============
    /**
     * 功能模块
     */
    private String module;

    /**
     * 操作类型
     */
    private String operation;

    // ============ 请求信息 ============
    /**
     * 请求URL
     */
    private String requestUrl;

    /**
     * 请求方法：GET/POST/PUT/DELETE
     */
    private String requestMethod;

    /**
     * 请求参数（JSON字符串）
     */
    private String requestParams;

    /**
     * 响应时间（毫秒）
     */
    private Long responseTime;

    // ============ 网络信息 ============
    /**
     * 客户端IP
     */
    private String ip;

    /**
     * 用户代理
     */
    private String userAgent;

    // ============ 扩展信息 ============
    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 上下文信息（用于简化接口）
     * 最终会合并到 extra 中
     */
    private Map<String, Object> context;

    /**
     * 扩展字段（google.protobuf.Struct）
     * 支持任意 JSON 结构
     */
    private Struct extra;

    // ============ 临时字段 ============
    /**
     * 异常对象（用于自动提取异常信息）
     */
    private transient Throwable throwable;

    /**
     * 异常类型（兼容旧版本，已废弃）
     * @deprecated 使用 exception 字段
     */
    @Deprecated
    private String exceptionType;

    /**
     * 堆栈信息（兼容旧版本，已废弃）
     * @deprecated 使用 exception 字段
     */
    @Deprecated
    private String stackTrace;

    // ============ 辅助方法 ============

    /**
     * 设置异常信息
     * 自动合并异常类型和堆栈信息
     */
    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
        if (throwable != null) {
            this.exception = formatThrowable(throwable);
            // 兼容旧字段
            this.exceptionType = throwable.getClass().getName();
            this.stackTrace = this.exception;
        }
    }

    /**
     * 格式化异常信息
     */
    private String formatThrowable(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getName());
        if (throwable.getMessage() != null) {
            sb.append(": ").append(throwable.getMessage());
        }
        sb.append("\n");

        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        Throwable cause = throwable.getCause();
        if (cause != null) {
            sb.append("Caused by: ").append(formatThrowable(cause));
        }

        return sb.toString();
    }

    /**
     * 添加标签
     */
    public void addTag(String tag) {
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        }
        if (!this.tags.contains(tag)) {
            this.tags.add(tag);
        }
    }

    /**
     * 添加上下文信息（简化接口）
     * 最终会合并到 extra 中
     */
    public void putContext(String key, Object value) {
        if (this.context == null) {
            this.context = new java.util.HashMap<>();
        }
        this.context.put(key, value);
    }

    /**
     * 设置 extra（从 Map 构建 Struct）
     * 用于简化接口
     */
    public void setExtraMap(Map<String, Object> extraMap) {
        if (extraMap != null && !extraMap.isEmpty()) {
            this.extra = mapToStruct(extraMap);
        }
    }

    /**
     * 获取 extra 的 Map 表示
     */
    public Map<String, Object> getExtraMap() {
        if (this.extra != null) {
            return structToMap(this.extra);
        }
        return null;
    }

    /**
     * 将 Map 转换为 Struct
     */
    public static Struct mapToStruct(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Struct.getDefaultInstance();
        }

        Struct.Builder structBuilder = Struct.newBuilder();
        map.forEach((key, value) -> {
            structBuilder.putFields(key, objectToValue(value));
        });

        return structBuilder.build();
    }

    /**
     * 将 Struct 转换为 Map
     */
    public static Map<String, Object> structToMap(Struct struct) {
        if (struct == null) {
            return new java.util.HashMap<>();
        }

        Map<String, Object> map = new java.util.HashMap<>();
        struct.getFieldsMap().forEach((key, value) -> {
            map.put(key, valueToObject(value));
        });

        return map;
    }

    /**
     * 将 Java 对象转换为 protobuf Value
     */
    private static Value objectToValue(Object obj) {
        if (obj == null) {
            return Value.newBuilder().setNullValue(com.google.protobuf.NullValue.NULL_VALUE).build();
        } else if (obj instanceof String) {
            return Value.newBuilder().setStringValue((String) obj).build();
        } else if (obj instanceof Number) {
            return Value.newBuilder().setNumberValue(((Number) obj).doubleValue()).build();
        } else if (obj instanceof Boolean) {
            return Value.newBuilder().setBoolValue((Boolean) obj).build();
        } else if (obj instanceof List) {
            com.google.protobuf.ListValue.Builder listBuilder = com.google.protobuf.ListValue.newBuilder();
            for (Object item : (List<?>) obj) {
                listBuilder.addValues(objectToValue(item));
            }
            return Value.newBuilder().setListValue(listBuilder.build()).build();
        } else if (obj instanceof Map) {
            return Value.newBuilder().setStructValue(mapToStruct((Map<String, Object>) obj)).build();
        } else {
            // 其他类型转为字符串
            return Value.newBuilder().setStringValue(obj.toString()).build();
        }
    }

    /**
     * 将 protobuf Value 转换为 Java 对象
     */
    private static Object valueToObject(Value value) {
        switch (value.getKindCase()) {
            case NULL_VALUE:
                return null;
            case NUMBER_VALUE:
                return value.getNumberValue();
            case STRING_VALUE:
                return value.getStringValue();
            case BOOL_VALUE:
                return value.getBoolValue();
            case STRUCT_VALUE:
                return structToMap(value.getStructValue());
            case LIST_VALUE:
                List<Object> list = new ArrayList<>();
                for (Value item : value.getListValue().getValuesList()) {
                    list.add(valueToObject(item));
                }
                return list;
            default:
                return null;
        }
    }
}