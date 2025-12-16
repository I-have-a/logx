package com.domidodo.logx.sdk.core;

import com.domidodo.logx.sdk.core.buffer.LogBuffer;
import com.domidodo.logx.sdk.core.config.LogXConfig;
import com.domidodo.logx.sdk.core.model.LogEntry;
import com.domidodo.logx.sdk.core.sender.GrpcLogSender;
import com.domidodo.logx.sdk.core.sender.HttpLogSender;
import com.domidodo.logx.sdk.core.sender.LogSender;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * LogX 客户端
 * 支持 HTTP 和 gRPC 两种模式
 */
@Slf4j
public class LogXClient {

    private final LogXConfig config;
    private final LogSender sender;
    private final LogBuffer buffer;
    private final ScheduledExecutorService scheduler;

    /**
     * 日志级别枚举
     */
    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    private LogXClient(LogXConfig config) {
        this.config = config;

        // 根据配置选择发送器
        if ("grpc".equalsIgnoreCase(config.getMode())) {
            this.sender = new GrpcLogSender(config);
            log.info("LogX SDK initialized with gRPC mode");
        } else {
            this.sender = new HttpLogSender(config);
            log.info("LogX SDK initialized with HTTP mode");
        }

        this.buffer = new LogBuffer(config.getBufferSize());
        this.scheduler = Executors.newScheduledThreadPool(1);

        // 启动定时刷新任务
        startFlushTask();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 记录 INFO 日志
     */
    public void info(String message) {
        log(Level.INFO, message, null, null);
    }

    public void info(String message, Map<String, Object> context) {
        log(Level.INFO, message, null, context);
    }

    /**
     * 记录 ERROR 日志
     */
    public void error(String message) {
        log(Level.ERROR, message, null, null);
    }

    public void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable, null);
    }

    public void error(String message, Throwable throwable, Map<String, Object> context) {
        log(Level.ERROR, message, throwable, context);
    }

    /**
     * 记录 WARN 日志
     */
    public void warn(String message) {
        log(Level.WARN, message, null, null);
    }

    public void warn(String message, Map<String, Object> context) {
        log(Level.WARN, message, null, context);
    }

    /**
     * 记录 DEBUG 日志
     */
    public void debug(String message) {
        log(Level.DEBUG, message, null, null);
    }

    public void debug(String message, Map<String, Object> context) {
        log(Level.DEBUG, message, null, context);
    }

    /**
     * 核心日志记录方法
     */
    private void log(Level level, String message, Throwable throwable, Map<String, Object> context) {
        try {
            LogEntry entry = LogEntry.builder()
                    .id(UUID.randomUUID().toString().replace("-", ""))
                    .tenantId(config.getTenantId())
                    .systemId(config.getSystemId())
                    .systemName(config.getSystemName())
                    .level(level.name())
                    .message(message)
                    .timestamp(LocalDateTime.now())
                    .context(context)
                    .build();

            // 添加异常信息
            if (throwable != null) {
                entry.setExceptionType(throwable.getClass().getName());
                entry.setStackTrace(getStackTrace(throwable));
            }

            // 添加到缓冲区
            if (config.isBufferEnabled()) {
                buffer.add(entry);

                // 缓冲区满时立即刷新
                if (buffer.isFull()) {
                    flush();
                }
            } else {
                // 直接发送
                sender.send(entry);
            }
        } catch (Exception e) {
            log.error("记录日志失败", e);
        }
    }

    /**
     * 手动刷新缓冲区
     */
    public void flush() {
        if (!buffer.isEmpty()) {
            try {
                sender.sendBatch(buffer.drain());
            } catch (Exception e) {
                log.error("刷新日志缓冲区失败", e);
            }
        }
    }

    /**
     * 启动定时刷新任务
     */
    private void startFlushTask() {
        if (config.isBufferEnabled()) {
            long interval = config.getFlushInterval().toMillis();
            scheduler.scheduleAtFixedRate(
                    this::flush,
                    interval,
                    interval,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * 关闭客户端
     */
    public void shutdown() {
        try {
            // 刷新剩余日志
            flush();

            // 关闭定时任务
            scheduler.shutdown();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);

            // 关闭发送器
            if (sender instanceof GrpcLogSender) {
                ((GrpcLogSender) sender).shutdown();
            }

            log.info("LogX SDK 已关闭");
        } catch (Exception e) {
            log.error("关闭 LogX SDK 失败", e);
        }
    }

    /**
     * 获取异常堆栈
     */
    private String getStackTrace(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");

        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        Throwable cause = throwable.getCause();
        if (cause != null) {
            sb.append("Caused by: ").append(getStackTrace(cause));
        }

        return sb.toString();
    }

    /**
     * Builder
     */
    public static class Builder {
        private final LogXConfig config = new LogXConfig();

        public Builder tenantId(Long tenantId) {
            config.setTenantId(tenantId);
            return this;
        }

        public Builder systemId(Long systemId) {
            config.setSystemId(systemId);
            return this;
        }

        public Builder systemName(String systemName) {
            config.setSystemName(systemName);
            return this;
        }

        public Builder apiKey(String apiKey) {
            config.setApiKey(apiKey);
            return this;
        }

        /**
         * 设置模式：http 或 grpc
         */
        public Builder mode(String mode) {
            config.setMode(mode);
            return this;
        }

        /**
         * HTTP 模式配置
         */
        public Builder gatewayUrl(String url) {
            config.setGatewayUrl(url);
            config.setMode("http");
            return this;
        }

        /**
         * gRPC 模式配置
         */
        public Builder grpcEndpoint(String host, int port) {
            config.setGrpcHost(host);
            config.setGrpcPort(port);
            config.setMode("grpc");
            return this;
        }

        public Builder bufferEnabled(boolean enabled) {
            config.setBufferEnabled(enabled);
            return this;
        }

        public Builder bufferSize(int size) {
            config.setBufferSize(size);
            return this;
        }

        public LogXClient build() {
            // 验证必填参数
            if (config.getTenantId() == null) {
                throw new IllegalArgumentException("tenantId 不能为空");
            }
            if (config.getSystemId() == null) {
                throw new IllegalArgumentException("systemId 不能为空");
            }
            if (config.getApiKey() == null) {
                throw new IllegalArgumentException("apiKey 不能为空");
            }

            // 验证网关配置
            if ("grpc".equalsIgnoreCase(config.getMode())) {
                if (config.getGrpcHost() == null) {
                    throw new IllegalArgumentException("gRPC 模式下 grpcHost 不能为空");
                }
            } else {
                if (config.getGatewayUrl() == null) {
                    throw new IllegalArgumentException("HTTP 模式下 gatewayUrl 不能为空");
                }
            }

            return new LogXClient(config);
        }
    }
}