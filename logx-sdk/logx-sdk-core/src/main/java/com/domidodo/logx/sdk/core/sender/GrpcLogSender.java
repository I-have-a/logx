package com.domidodo.logx.sdk.core.sender;

import com.domidodo.logx.common.grpc.*;
import com.domidodo.logx.sdk.core.HeaderClientInterceptor;
import com.domidodo.logx.sdk.core.config.LogXConfig;
import com.domidodo.logx.sdk.core.model.LogEntry;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 日志发送器
 * 使用重构后的 proto 定义
 */
@Slf4j
public class GrpcLogSender implements LogSender {

    private final LogXConfig config;
    private final ManagedChannel channel;
    private final LogServiceGrpc.LogServiceBlockingStub blockingStub;
    private final LogServiceGrpc.LogServiceStub asyncStub;

    /**
     * Metadata Keys
     */
    private static final Metadata.Key<String> API_KEY_METADATA_KEY =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> TENANT_ID_METADATA_KEY =
            Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> SYSTEM_ID_METADATA_KEY =
            Metadata.Key.of("x-system-id", Metadata.ASCII_STRING_MARSHALLER);

    public GrpcLogSender(LogXConfig config) {
        this.config = config;

        // 创建 gRPC Channel
        this.channel = ManagedChannelBuilder
                .forAddress(config.getGrpcHost(), config.getGrpcPort())
                .usePlaintext()
                .maxInboundMessageSize(10 * 1024 * 1024) // 10MB
                .build();

        // 创建带认证的 Stub
        Metadata metadata = createMetadata();

        ClientInterceptor interceptor = new HeaderClientInterceptor(metadata);

        Channel interceptedChannel =
                ClientInterceptors.intercept(channel, interceptor);

        this.blockingStub = LogServiceGrpc.newBlockingStub(interceptedChannel);
        this.asyncStub = LogServiceGrpc.newStub(interceptedChannel);

    }

    /**
     * 发送单条日志（实际使用批量接口）
     */
    @Override
    public void send(LogEntry entry) {
        try {
            // 将单条日志包装为批量请求
            LogBatchRequest request = LogBatchRequest.newBuilder()
                    .setTenantId(String.valueOf(config.getTenantId()))
                    .setSystemId(String.valueOf(config.getSystemId()))
                    .setApiKey(config.getApiKey())
                    .addLogs(buildLogEntry(entry))
                    .build();

            LogBatchResponse response = blockingStub.sendLogs(request);

            if (!response.getSuccess()) {
                log.error("gRPC 发送失败: {}", response.getMessage());
            } else {
                log.debug("gRPC 发送成功: {}", response.getMessage());
            }
        } catch (Exception e) {
            log.error("gRPC 发送异常", e);
        }
    }

    /**
     * 批量发送日志（推荐使用）
     */
    @Override
    public void sendBatch(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        try {
            // 构建批量请求
            LogBatchRequest.Builder requestBuilder = LogBatchRequest.newBuilder()
                    .setTenantId(String.valueOf(config.getTenantId()))
                    .setSystemId(String.valueOf(config.getSystemId()))
                    .setApiKey(config.getApiKey());

            // 添加所有日志
            for (LogEntry entry : entries) {
                requestBuilder.addLogs(buildLogEntry(entry));
            }

            // 同步发送
            LogBatchResponse response = blockingStub.sendLogs(requestBuilder.build());

            if (response.getSuccess()) {
                log.debug("gRPC 批量发送成功: 接收={}, 成功={}, 失败={}",
                        response.getReceived(), response.getSuccessCount(), response.getFailedCount());
            } else {
                log.error("gRPC 批量发送失败: {}", response.getMessage());
            }
        } catch (Exception e) {
            log.error("gRPC 批量发送异常", e);
        }
    }

    /**
     * 流式发送日志（异步）
     */
    public void sendBatchStream(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);

        // 响应观察者
        StreamObserver<LogBatchResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(LogBatchResponse response) {
                if (response.getSuccess()) {
                    log.debug("gRPC 流式发送成功: 接收={}, 成功={}, 失败={}",
                            response.getReceived(), response.getSuccessCount(), response.getFailedCount());
                } else {
                    log.error("gRPC 流式发送失败: {}", response.getMessage());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("gRPC 流式发送异常", t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        };

        // 请求观察者
        StreamObserver<com.domidodo.logx.common.grpc.LogEntry> requestObserver =
                asyncStub.streamLogs(responseObserver);

        try {
            // 发送所有日志
            for (LogEntry entry : entries) {
                com.domidodo.logx.common.grpc.LogEntry grpcLogEntry = buildLogEntry(entry);
                requestObserver.onNext(grpcLogEntry);
            }

            // 完成发送
            requestObserver.onCompleted();

            // 等待响应
            latch.await(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("gRPC 流式发送异常", e);
            requestObserver.onError(e);
        }
    }

    /**
     * 构建 gRPC LogEntry
     */
    private com.domidodo.logx.common.grpc.LogEntry buildLogEntry(LogEntry entry) {
        com.domidodo.logx.common.grpc.LogEntry.Builder builder =
                com.domidodo.logx.common.grpc.LogEntry.newBuilder();

        // 基础信息
        builder.setTraceId(entry.getTraceId() != null ? entry.getTraceId() : "");
        builder.setSpanId(entry.getSpanId() != null ? entry.getSpanId() : "");
        builder.setTenantId(String.valueOf(config.getTenantId()));
        builder.setSystemId(String.valueOf(config.getSystemId()));

        // 时间戳
        if (entry.getTimestamp() != null) {
            builder.setTimestamp(
                    entry.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            );
        } else {
            builder.setTimestamp(System.currentTimeMillis());
        }

        // 日志级别和内容
        builder.setLevel(entry.getLevel() != null ? entry.getLevel() : "INFO");
        builder.setMessage(entry.getMessage() != null ? entry.getMessage() : "");

        // 代码位置信息（从堆栈中提取）
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length > 3) {
            StackTraceElement element = stackTrace[3]; // 跳过 getStackTrace、buildLogEntry、log 方法
            builder.setClassName(element.getClassName());
            builder.setMethodName(element.getMethodName());
            builder.setLineNumber(element.getLineNumber());
        }

        // 异常信息
        if (entry.getExceptionType() != null) {
            builder.setException(entry.getExceptionType() + "\n" +
                                 (entry.getStackTrace() != null ? entry.getStackTrace() : ""));
        }

        // 上下文信息
        if (entry.getContext() != null) {
            entry.getContext().forEach((key, value) -> {
                if (value != null) {
                    builder.putExtra(key, value.toString());
                }
            });
        }

        return builder.build();
    }

    /**
     * 创建认证 Metadata
     */
    private Metadata createMetadata() {
        Metadata metadata = new Metadata();

        if (config.getApiKey() != null) {
            metadata.put(API_KEY_METADATA_KEY, config.getApiKey());
        }

        metadata.put(TENANT_ID_METADATA_KEY, String.valueOf(config.getTenantId()));
        metadata.put(SYSTEM_ID_METADATA_KEY, String.valueOf(config.getSystemId()));

        return metadata;
    }

    /**
     * 关闭连接
     */
    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            log.info("gRPC channel closed");
        } catch (InterruptedException e) {
            log.error("Error shutting down gRPC channel", e);
            Thread.currentThread().interrupt();
        }
    }
}