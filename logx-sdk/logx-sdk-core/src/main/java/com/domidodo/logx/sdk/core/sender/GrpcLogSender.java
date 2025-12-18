package com.domidodo.logx.sdk.core.sender;

import com.domidodo.logx.common.grpc.*;
import com.domidodo.logx.sdk.core.HeaderClientInterceptor;
import com.domidodo.logx.sdk.core.config.LogXConfig;
import com.domidodo.logx.sdk.core.model.LogEntry;
import com.google.protobuf.Struct;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 日志发送器
 * 支持 google.protobuf.Struct 类型的 extra 字段
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
                .maxInboundMessageSize(config.getGrpcMaxInboundMessageSize())
                .build();

        // 创建带认证的 Stub
        Metadata metadata = createMetadata();
        ClientInterceptor interceptor = new HeaderClientInterceptor(metadata);
        Channel interceptedChannel = ClientInterceptors.intercept(channel, interceptor);

        this.blockingStub = LogServiceGrpc.newBlockingStub(interceptedChannel);
        this.asyncStub = LogServiceGrpc.newStub(interceptedChannel);
    }

    /**
     * 发送单条日志（实际使用批量接口）
     */
    @Override
    public void send(LogEntry entry) {
        try {
            LogBatchRequest request = LogBatchRequest.newBuilder()
                    .setTenantId(config.getTenantId())
                    .setSystemId(config.getSystemId())
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
                    .setTenantId(config.getTenantId())
                    .setSystemId(config.getSystemId())
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
     * 构建 gRPC LogEntry（支持所有字段和 Struct）
     */
    private com.domidodo.logx.common.grpc.LogEntry buildLogEntry(LogEntry entry) {
        com.domidodo.logx.common.grpc.LogEntry.Builder builder =
                com.domidodo.logx.common.grpc.LogEntry.newBuilder();

        // ============ 追踪信息 ============
        if (entry.getTraceId() != null) {
            builder.setTraceId(entry.getTraceId());
        }
        if (entry.getSpanId() != null) {
            builder.setSpanId(entry.getSpanId());
        }

        // ============ 租户信息 ============
        builder.setTenantId(config.getTenantId());
        builder.setSystemId(config.getSystemId());

        // ============ 时间戳 ============
        if (entry.getTimestamp() != null) {
            builder.setTimestamp(
                    entry.getTimestamp()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
            );
        } else {
            builder.setTimestamp(System.currentTimeMillis());
        }

        // ============ 日志基础信息 ============
        if (entry.getLevel() != null) {
            builder.setLevel(entry.getLevel());
        }
        if (entry.getLogger() != null) {
            builder.setLogger(entry.getLogger());
        }
        if (entry.getThread() != null) {
            builder.setThread(entry.getThread());
        }

        // ============ 代码位置 ============
        if (entry.getClassName() != null) {
            builder.setClassName(entry.getClassName());
        }
        if (entry.getMethodName() != null) {
            builder.setMethodName(entry.getMethodName());
        }
        if (entry.getLineNumber() != null) {
            builder.setLineNumber(entry.getLineNumber());
        }

        // ============ 日志内容 ============
        if (entry.getMessage() != null) {
            builder.setMessage(entry.getMessage());
        }
        if (entry.getException() != null) {
            builder.setException(entry.getException());
        }

        // ============ 用户信息 ============
        if (entry.getUserId() != null) {
            builder.setUserId(entry.getUserId());
        }
        if (entry.getUserName() != null) {
            builder.setUserName(entry.getUserName());
        }

        // ============ 业务信息 ============
        if (entry.getModule() != null) {
            builder.setModule(entry.getModule());
        }
        if (entry.getOperation() != null) {
            builder.setOperation(entry.getOperation());
        }

        // ============ 请求信息 ============
        if (entry.getRequestUrl() != null) {
            builder.setRequestUrl(entry.getRequestUrl());
        }
        if (entry.getRequestMethod() != null) {
            builder.setRequestMethod(entry.getRequestMethod());
        }
        if (entry.getRequestParams() != null) {
            builder.setRequestParams(entry.getRequestParams());
        }
        if (entry.getResponseTime() != null) {
            builder.setResponseTime(entry.getResponseTime());
        }

        // ============ 网络信息 ============
        if (entry.getIp() != null) {
            builder.setIp(entry.getIp());
        }
        if (entry.getUserAgent() != null) {
            builder.setUserAgent(entry.getUserAgent());
        }

        // ============ 扩展信息 ============
        // 标签列表
        if (entry.getTags() != null && !entry.getTags().isEmpty()) {
            builder.addAllTags(entry.getTags());
        }

        // 扩展字段（google.protobuf.Struct）
        Struct extra = buildExtraStruct(entry);
        if (extra.getFieldsCount() > 0) {
            builder.setExtra(extra);
        }

        return builder.build();
    }

    /**
     * 构建 extra Struct
     * 合并 entry.extra 和 entry.context
     */
    private Struct buildExtraStruct(LogEntry entry) {
        Struct.Builder structBuilder = Struct.newBuilder();

        // 1. 首先添加 context 中的内容
        if (entry.getContext() != null && !entry.getContext().isEmpty()) {
            Struct contextStruct = LogEntry.mapToStruct(entry.getContext());
            structBuilder.putAllFields(contextStruct.getFieldsMap());
        }

        // 2. 然后添加 extra 中的内容（会覆盖同名的 context 字段）
        if (entry.getExtra() != null && entry.getExtra().getFieldsCount() > 0) {
            structBuilder.putAllFields(entry.getExtra().getFieldsMap());
        }

        return structBuilder.build();
    }

    /**
     * 创建认证 Metadata
     */
    private Metadata createMetadata() {
        Metadata metadata = new Metadata();

        if (config.getApiKey() != null) {
            metadata.put(API_KEY_METADATA_KEY, config.getApiKey());
        }

        metadata.put(TENANT_ID_METADATA_KEY, config.getTenantId());
        metadata.put(SYSTEM_ID_METADATA_KEY, config.getSystemId());

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