package com.domidodo.logx.sdk.core;

import com.domidodo.logx.sdk.core.buffer.LogBuffer;
import com.domidodo.logx.sdk.core.config.LogXConfig;
import com.domidodo.logx.sdk.core.model.LogEntry;
import com.domidodo.logx.sdk.core.sender.GrpcLogSender;
import com.domidodo.logx.sdk.core.sender.HttpLogSender;
import com.domidodo.logx.sdk.core.sender.LogSender;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * LogX 客户端
 * 支持 HTTP 和 gRPC 两种模式
 * 支持 google.protobuf.Struct 类型的扩展字段
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
            log.info("LogX SDK 已用 gRPC 模式初始化");
        } else {
            this.sender = new HttpLogSender(config);
            log.info("LogX SDK 已用 HTTP 模式初始化");
        }

        this.buffer = new LogBuffer(config.getBufferSize());
        this.scheduler = Executors.newScheduledThreadPool(1);

        // 启动定时刷新任务
        startFlushTask();
    }

    public static Builder builder() {
        return new Builder();
    }

    // ============ 简化的日志方法 ============

    /**
     * 记录 INFO 日志
     */
    public void info(String message) {
        log(Level.INFO, message, null, null);
    }

    /**
     * 记录 INFO 日志（带扩展字段）
     */
    public void info(String message, Map<String, Object> extra) {
        log(Level.INFO, message, null, extra);
    }

    /**
     * 记录 ERROR 日志
     */
    public void error(String message) {
        log(Level.ERROR, message, null, null);
    }

    /**
     * 记录 ERROR 日志（带异常）
     */
    public void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable, null);
    }

    /**
     * 记录 ERROR 日志（带异常和扩展字段）
     */
    public void error(String message, Throwable throwable, Map<String, Object> extra) {
        log(Level.ERROR, message, throwable, extra);
    }

    /**
     * 记录 WARN 日志
     */
    public void warn(String message) {
        log(Level.WARN, message, null, null);
    }

    /**
     * 记录 WARN 日志（带扩展字段）
     */
    public void warn(String message, Map<String, Object> extra) {
        log(Level.WARN, message, null, extra);
    }

    /**
     * 记录 DEBUG 日志
     */
    public void debug(String message) {
        log(Level.DEBUG, message, null, null);
    }

    /**
     * 记录 DEBUG 日志（带扩展字段）
     */
    public void debug(String message, Map<String, Object> extra) {
        log(Level.DEBUG, message, null, extra);
    }

    // ============ 完整的日志方法 ============

    /**
     * 记录完整日志（支持所有字段）
     */
    public void log(LogEntry entry) {
        try {
            // 补充基础信息
            if (entry.getId() == null) {
                entry.setId(UUID.randomUUID().toString().replace("-", ""));
            }
            if (entry.getTenantId() == null) {
                entry.setTenantId(config.getTenantId());
            }
            if (entry.getSystemId() == null) {
                entry.setSystemId(config.getSystemId());
            }
            if (entry.getSystemName() == null) {
                entry.setSystemName(config.getSystemName());
            }
            if (entry.getTimestamp() == null) {
                entry.setTimestamp(LocalDateTime.now());
            }

            // 自动填充代码位置信息
            if (entry.getClassName() == null || entry.getMethodName() == null) {
                fillCodeLocation(entry);
            }

            // 处理异常对象
            if (entry.getThrowable() != null && entry.getException() == null) {
                entry.setThrowable(entry.getThrowable());
            }

            // 添加到缓冲区或直接发送
            if (config.isBufferEnabled()) {
                buffer.add(entry);
                if (buffer.isFull()) {
                    flush();
                }
            } else {
                sender.send(entry);
            }
        } catch (Exception e) {
            log.error("记录日志失败", e);
        }
    }

    /**
     * 核心日志记录方法
     * extra 参数会被转换为 google.protobuf.Struct
     */
    private void log(Level level, String message, Throwable throwable, Map<String, Object> extra) {
        try {
            LogEntry.LogEntryBuilder entryBuilder = LogEntry.builder()
                    .id(UUID.randomUUID().toString().replace("-", ""))
                    .tenantId(config.getTenantId())
                    .systemId(config.getSystemId())
                    .systemName(config.getSystemName())
                    .level(level.name())
                    .message(message)
                    .timestamp(LocalDateTime.now());

            // 添加扩展字段（会被转换为 Struct）
            if (extra != null && !extra.isEmpty()) {
                entryBuilder.context(extra);  // 使用 context，最终会合并到 extra Struct 中
            }

            LogEntry entry = entryBuilder.build();

            // 添加异常信息
            if (throwable != null) {
                entry.setThrowable(throwable);
            }

            // 自动填充代码位置信息
            fillCodeLocation(entry);

            // 添加到缓冲区或直接发送
            if (config.isBufferEnabled()) {
                buffer.add(entry);
                if (buffer.isFull()) {
                    flush();
                }
            } else {
                sender.send(entry);
            }
        } catch (Exception e) {
            log.error("记录日志失败", e);
        }
    }

    /**
     * 自动填充代码位置信息
     */
    private void fillCodeLocation(LogEntry entry) {
        try {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            // 跳过 getStackTrace, fillCodeLocation, log 方法
            // 通常需要跳过 4-5 层才能到达实际调用位置
            for (int i = 4; i < Math.min(stackTrace.length, 10); i++) {
                StackTraceElement element = stackTrace[i];
                String className = element.getClassName();

                // 跳过 LogXClient 和 LogXLogger 类
                if (className.contains("LogXClient") || className.contains("LogXLogger")) {
                    continue;
                }

                // 找到第一个用户代码
                if (entry.getClassName() == null) {
                    entry.setClassName(className);
                }
                if (entry.getMethodName() == null) {
                    entry.setMethodName(element.getMethodName());
                }
                if (entry.getLineNumber() == null) {
                    entry.setLineNumber(element.getLineNumber());
                }
                break;
            }

            // 填充线程名
            if (entry.getThread() == null) {
                entry.setThread(Thread.currentThread().getName());
            }
        } catch (Exception e) {
            // 忽略错误，代码位置信息不是必需的
            log.debug("Failed to fill code location", e);
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
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }

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
     * Builder
     */
    public static class Builder {
        private final LogXConfig config = new LogXConfig();

        /**
         * 设置租户ID（使用 String 类型）
         */
        public Builder tenantId(String tenantId) {
            config.setTenantId(tenantId);
            return this;
        }

        /**
         * 设置租户ID（兼容 Long 类型）
         */
        @Deprecated
        public Builder tenantId(Long tenantId) {
            config.setTenantId(String.valueOf(tenantId));
            return this;
        }

        /**
         * 设置系统ID（使用 String 类型）
         */
        public Builder systemId(String systemId) {
            config.setSystemId(systemId);
            return this;
        }

        /**
         * 设置系统ID（兼容 Long 类型）
         */
        @Deprecated
        public Builder systemId(Long systemId) {
            config.setSystemId(String.valueOf(systemId));
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

        public Builder batchMode(String match) {
            config.setBatchMode(match);
            return this;
        }

        public Builder flushInterval(Duration interval) {
            config.setFlushInterval(interval);
            return this;
        }

        public Builder connectTimeout(int timeout) {
            config.setConnectTimeout(timeout);
            return this;
        }

        public LogXClient build() {
            // 验证必填参数
            if (config.getTenantId() == null || config.getTenantId().isEmpty()) {
                throw new IllegalArgumentException("tenantId 不能为空");
            }
            if (config.getSystemId() == null || config.getSystemId().isEmpty()) {
                throw new IllegalArgumentException("systemId 不能为空");
            }
            if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
                throw new IllegalArgumentException("apiKey 不能为空");
            }

            // 验证网关配置
            if ("grpc".equalsIgnoreCase(config.getMode())) {
                if (config.getGrpcHost() == null || config.getGrpcHost().isEmpty()) {
                    throw new IllegalArgumentException("gRPC 模式下 grpcHost 不能为空");
                }
            } else {
                if (config.getGatewayUrl() == null || config.getGatewayUrl().isEmpty()) {
                    throw new IllegalArgumentException("HTTP 模式下 gatewayUrl 不能为空");
                }
            }

            return new LogXClient(config);
        }
    }
}