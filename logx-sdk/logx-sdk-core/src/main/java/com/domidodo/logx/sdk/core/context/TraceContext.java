package com.domidodo.logx.sdk.core.context;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 分布式追踪上下文管理
 *
 * 放在 core 模块，供 gateway-starter 和 spring-boot-starter 共同使用
 *
 * 支持两种环境：
 * - Servlet（ThreadLocal）：spring-boot-starter 使用
 * - WebFlux（Reactor Context）：gateway-starter 使用
 */
public class TraceContext {

    // ============ Header 常量 ============
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String SPAN_ID_HEADER = "X-Span-Id";
    public static final String PARENT_SPAN_ID_HEADER = "X-Parent-Span-Id";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_NAME_HEADER = "X-User-Name";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    // ============ Reactor Context Key（WebFlux 使用） ============
    public static final String TRACE_CONTEXT_KEY = "logx-trace-context";

    // ============ ThreadLocal（Servlet 环境） ============
    private static final ThreadLocal<TraceInfo> CONTEXT = new ThreadLocal<>();

    /**
     * 追踪信息
     */
    @Data
    public static class TraceInfo {
        // Getters and Setters
        private String traceId;
        private String spanId;
        private String parentSpanId;
        private String userId;
        private String userName;
        private String tenantId;
        private long startTime;

        public TraceInfo() {
            this.startTime = System.currentTimeMillis();
        }

        /**
         * 计算响应时间（毫秒）
         */
        public long getResponseTime() {
            return System.currentTimeMillis() - startTime;
        }

        @Override
        public String toString() {
            return "TraceInfo{" +
                   "traceId='" + traceId + '\'' +
                   ", spanId='" + spanId + '\'' +
                   ", parentSpanId='" + parentSpanId + '\'' +
                   ", userId='" + userId + '\'' +
                   '}';
        }
    }

    // ============ ID 生成 ============

    /**
     * 生成 TraceId（32位十六进制）
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成 SpanId（16位十六进制）
     */
    public static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ============ ThreadLocal 操作（Servlet 环境） ============

    /**
     * 设置追踪信息
     */
    public static void setTrace(TraceInfo traceInfo) {
        CONTEXT.set(traceInfo);
    }

    /**
     * 获取追踪信息
     */
    public static TraceInfo getTrace() {
        return CONTEXT.get();
    }

    /**
     * 获取 TraceId
     */
    public static String getTraceId() {
        TraceInfo info = CONTEXT.get();
        return info != null ? info.getTraceId() : null;
    }

    /**
     * 获取 SpanId
     */
    public static String getSpanId() {
        TraceInfo info = CONTEXT.get();
        return info != null ? info.getSpanId() : null;
    }

    /**
     * 获取用户ID
     */
    public static String getUserId() {
        TraceInfo info = CONTEXT.get();
        return info != null ? info.getUserId() : null;
    }

    /**
     * 获取用户名
     */
    public static String getUserName() {
        TraceInfo info = CONTEXT.get();
        return info != null ? info.getUserName() : null;
    }

    /**
     * 清除追踪信息（请求结束时调用）
     */
    public static void clear() {
        CONTEXT.remove();
    }

    // ============ 工厂方法 ============

    /**
     * 创建新的追踪信息
     *
     * @param traceId 已有的 TraceId，为 null 则生成新的
     * @param parentSpanId 父 SpanId
     * @return 追踪信息
     */
    public static TraceInfo createTrace(String traceId, String parentSpanId) {
        TraceInfo info = new TraceInfo();
        info.setTraceId(traceId != null && !traceId.isEmpty() ? traceId : generateTraceId());
        info.setSpanId(generateSpanId());
        info.setParentSpanId(parentSpanId);
        return info;
    }

    /**
     * 创建新的追踪信息（无父级）
     */
    public static TraceInfo createTrace() {
        return createTrace(null, null);
    }
}
