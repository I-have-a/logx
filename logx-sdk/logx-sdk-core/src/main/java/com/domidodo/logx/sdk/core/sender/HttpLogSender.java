package com.domidodo.logx.sdk.core.sender;

import com.domidodo.logx.sdk.core.config.LogXConfig;
import com.domidodo.logx.sdk.core.model.LogEntry;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * HTTP 日志发送器
 * 支持 google.protobuf.Struct（转换为 JSON）
 */
@Slf4j
public class HttpLogSender implements LogSender {

    private final LogXConfig config;
    private final String endpoint;

    public HttpLogSender(LogXConfig config) {
        this.config = config;
        this.endpoint = config.getGatewayUrl() + "/api/v1/logs";
    }

    @Override
    public void send(LogEntry entry) {
        sendBatch(List.of(entry));
    }

    @Override
    public void sendBatch(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        int retries = 0;
        while (retries < config.getMaxRetries()) {
            try {
                doSend(entries);
                return;
            } catch (Exception e) {
                retries++;
                if (retries >= config.getMaxRetries()) {
                    log.error("发送日志失败，已重试 {} 次", retries, e);
                } else {
                    log.warn("发送日志失败，正在重试 ({}/{})", retries, config.getMaxRetries());
                    try {
                        Thread.sleep(1000L * retries);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void doSend(List<LogEntry> entries) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("X-Tenant-Id", config.getTenantId());
            conn.setRequestProperty("X-System-Id", config.getSystemId());
            conn.setRequestProperty("X-API-Key", config.getApiKey());
            conn.setConnectTimeout(config.getConnectTimeout());
            conn.setReadTimeout(config.getReadTimeout());
            conn.setDoOutput(true);

            // JSON 序列化
            String json = toJson(entries);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new RuntimeException("HTTP 响应码: " + responseCode);
            }

            log.debug("成功发送 {} 条日志", entries.size());
        } finally {
            conn.disconnect();
        }
    }

    /**
     * JSON 序列化
     * 支持 google.protobuf.Struct（转换为 Map）
     */
    private String toJson(List<LogEntry> entries) {
        StringBuilder json = new StringBuilder("[");

        for (int i = 0; i < entries.size(); i++) {
            LogEntry entry = entries.get(i);
            json.append("{");

            // 基础字段
            appendField(json, "id", entry.getId(), true);
            appendField(json, "tenantId", entry.getTenantId(), false);
            appendField(json, "systemId", entry.getSystemId(), false);
            appendField(json, "systemName", entry.getSystemName(), false);
            appendField(json, "level", entry.getLevel(), false);
            appendField(json, "message", entry.getMessage(), false);

            // 时间戳
            if (entry.getTimestamp() != null) {
                json.append(",\"timestamp\":\"").append(entry.getTimestamp()).append("\"");
            }

            // 追踪信息
            appendField(json, "traceId", entry.getTraceId(), false);
            appendField(json, "spanId", entry.getSpanId(), false);

            // 日志基础信息
            appendField(json, "logger", entry.getLogger(), false);
            appendField(json, "thread", entry.getThread(), false);

            // 代码位置
            appendField(json, "className", entry.getClassName(), false);
            appendField(json, "methodName", entry.getMethodName(), false);
            if (entry.getLineNumber() != null) {
                json.append(",\"lineNumber\":").append(entry.getLineNumber());
            }

            // 异常信息
            appendField(json, "exception", entry.getException(), false);

            // 用户信息
            appendField(json, "userId", entry.getUserId(), false);
            appendField(json, "userName", entry.getUserName(), false);

            // 业务信息
            appendField(json, "module", entry.getModule(), false);
            appendField(json, "operation", entry.getOperation(), false);

            // 请求信息
            appendField(json, "requestUrl", entry.getRequestUrl(), false);
            appendField(json, "requestMethod", entry.getRequestMethod(), false);
            appendField(json, "requestParams", entry.getRequestParams(), false);
            if (entry.getResponseTime() != null) {
                json.append(",\"responseTime\":").append(entry.getResponseTime());
            }

            // 网络信息
            appendField(json, "ip", entry.getIp(), false);
            appendField(json, "userAgent", entry.getUserAgent(), false);

            // 标签
            if (entry.getTags() != null && !entry.getTags().isEmpty()) {
                json.append(",\"tags\":[");
                for (int j = 0; j < entry.getTags().size(); j++) {
                    json.append("\"").append(escape(entry.getTags().get(j))).append("\"");
                    if (j < entry.getTags().size() - 1) {
                        json.append(",");
                    }
                }
                json.append("]");
            }

            // 扩展字段（Struct -> Map -> JSON）
            Map<String, Object> extraMap = buildExtraMap(entry);
            if (!extraMap.isEmpty()) {
                json.append(",\"extra\":");
                appendMapAsJson(json, extraMap);
            }

            json.append("}");

            if (i < entries.size() - 1) {
                json.append(",");
            }
        }

        json.append("]");
        return json.toString();
    }

    /**
     * 构建 extra Map
     * 合并 context 和 extra Struct
     */
    private Map<String, Object> buildExtraMap(LogEntry entry) {
        Map<String, Object> result = new java.util.HashMap<>();

        // 1. 添加 context
        if (entry.getContext() != null) {
            result.putAll(entry.getContext());
        }

        // 2. 添加 extra（Struct 转 Map）
        if (entry.getExtra() != null) {
            Map<String, Object> extraMap = LogEntry.structToMap(entry.getExtra());
            result.putAll(extraMap);
        }

        return result;
    }

    /**
     * 添加字段到 JSON
     */
    private void appendField(StringBuilder json, String key, String value, boolean isFirst) {
        if (value != null && !value.isEmpty()) {
            if (!isFirst) {
                json.append(",");
            }
            json.append("\"").append(key).append("\":\"")
                    .append(escape(value)).append("\"");
        }
    }

    /**
     * 将 Map 转换为 JSON
     */
    private void appendMapAsJson(StringBuilder json, Map<String, Object> map) {
        json.append("{");
        int count = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (count > 0) {
                json.append(",");
            }
            json.append("\"").append(escape(entry.getKey())).append("\":");
            appendValueAsJson(json, entry.getValue());
            count++;
        }
        json.append("}");
    }

    /**
     * 将值转换为 JSON
     */
    private void appendValueAsJson(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof String) {
            json.append("\"").append(escape((String) value)).append("\"");
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else if (value instanceof List<?> list) {
            json.append("[");
            for (int i = 0; i < list.size(); i++) {
                appendValueAsJson(json, list.get(i));
                if (i < list.size() - 1) {
                    json.append(",");
                }
            }
            json.append("]");
        } else if (value instanceof Map) {
            appendMapAsJson(json, (Map<String, Object>) value);
        } else {
            // 其他类型转为字符串
            json.append("\"").append(escape(value.toString())).append("\"");
        }
    }

    /**
     * 转义 JSON 字符串
     */
    private String escape(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }
}