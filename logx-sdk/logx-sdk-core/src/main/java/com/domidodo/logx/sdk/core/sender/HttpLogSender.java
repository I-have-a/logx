package com.domidodo.logx.sdk.core.sender;

import com.domidodo.logx.sdk.core.config.LogXConfig;
import com.domidodo.logx.sdk.core.model.LogEntry;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Tenant-Id", String.valueOf(config.getTenantId()));
            conn.setConnectTimeout(config.getConnectTimeout());
            conn.setReadTimeout(config.getReadTimeout());
            conn.setDoOutput(true);

            // 简单的 JSON 序列化
            String json = toJson(entries);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("HTTP 响应码: " + responseCode);
            }

            log.debug("成功发送 {} 条日志", entries.size());
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 简单的 JSON 序列化（避免依赖第三方库）
     */
    private String toJson(List<LogEntry> entries) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            LogEntry entry = entries.get(i);
            json.append("{")
                    .append("\"id\":\"").append(entry.getId()).append("\",")
                    .append("\"tenantId\":").append(entry.getTenantId()).append(",")
                    .append("\"systemId\":").append(entry.getSystemId()).append(",")
                    .append("\"systemName\":\"").append(entry.getSystemName()).append("\",")
                    .append("\"level\":\"").append(entry.getLevel()).append("\",")
                    .append("\"message\":\"").append(escape(entry.getMessage())).append("\",")
                    .append("\"timestamp\":\"").append(entry.getTimestamp()).append("\"");

            if (entry.getExceptionType() != null) {
                json.append(",\"exceptionType\":\"").append(entry.getExceptionType()).append("\"");
            }
            if (entry.getStackTrace() != null) {
                json.append(",\"stackTrace\":\"").append(escape(entry.getStackTrace())).append("\"");
            }

            json.append("}");
            if (i < entries.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    private String escape(String str) {
        return str.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
