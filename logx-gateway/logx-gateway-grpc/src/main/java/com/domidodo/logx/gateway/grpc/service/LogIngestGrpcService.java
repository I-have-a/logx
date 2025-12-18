package com.domidodo.logx.gateway.grpc.service;

import com.domidodo.logx.common.context.TenantContext;
import com.domidodo.logx.common.grpc.*;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * gRPC 日志接收服务
 * 支持 google.protobuf.Struct 类型的 extra 字段
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class LogIngestGrpcService extends LogServiceGrpc.LogServiceImplBase {

    private final KafkaLogSender kafkaLogSender;

    @org.springframework.beans.factory.annotation.Value("${logx.batch.max-size:100}")
    private int maxBatchSize;

    /**
     * 批量接收日志
     */
    @Override
    public void sendLogs(LogBatchRequest request, StreamObserver<LogBatchResponse> responseObserver) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 参数校验
            if (request.getLogsList().isEmpty()) {
                LogBatchResponse response = LogBatchResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("日志列表不能为空")
                        .setReceived(0)
                        .setSuccessCount(0)
                        .setFailedCount(0)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }

            // 2. 检查批次大小
            int logCount = request.getLogsList().size();
            if (logCount > maxBatchSize) {
                LogBatchResponse response = LogBatchResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("批次大小超过限制，最大允许 " + maxBatchSize + " 条")
                        .setReceived(logCount)
                        .setSuccessCount(0)
                        .setFailedCount(logCount)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }

            // 3. 转换为 Map 格式
            List<Map<String, Object>> logs = request.getLogsList().stream()
                    .map(this::convertToMap)
                    .collect(Collectors.toList());

            // 4. 发送到 Kafka
            int successCount = kafkaLogSender.sendBatch(logs);
            int failedCount = logCount - successCount;

            // 5. 构建响应
            LogBatchResponse response = LogBatchResponse.newBuilder()
                    .setSuccess(successCount > 0)
                    .setReceived(logCount)
                    .setSuccessCount(successCount)
                    .setFailedCount(failedCount)
                    .setMessage(String.format("接收 %d 条日志，成功 %d 条，失败 %d 条",
                            logCount, successCount, failedCount))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            long duration = System.currentTimeMillis() - startTime;
            log.info("gRPC batch processed: tenant={}, system={}, logs={}, success={}, duration={}ms",
                    TenantContext.getTenantId(), TenantContext.getSystemId(),
                    logCount, successCount, duration);

        } catch (Exception e) {
            log.error("Error processing gRPC log batch", e);

            LogBatchResponse response = LogBatchResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("处理失败: " + e.getMessage())
                    .setReceived(request.getLogsList().size())
                    .setSuccessCount(0)
                    .setFailedCount(request.getLogsList().size())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 流式接收日志（客户端流式上传）
     */
    @Override
    public StreamObserver<com.domidodo.logx.common.grpc.LogEntry> streamLogs(
            StreamObserver<LogBatchResponse> responseObserver) {
        return new StreamObserver<>() {
            private int received = 0;
            private int success = 0;
            private int failed = 0;

            @Override
            public void onNext(com.domidodo.logx.common.grpc.LogEntry logEntry) {
                received++;
                try {
                    Map<String, Object> log = convertToMap(logEntry);
                    boolean sent = kafkaLogSender.send(log);
                    if (sent) {
                        success++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    log.error("Failed to process streamed log", e);
                    failed++;
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Error in stream logs", t);
                LogBatchResponse response = LogBatchResponse.newBuilder()
                        .setSuccess(false)
                        .setReceived(received)
                        .setSuccessCount(success)
                        .setFailedCount(failed)
                        .setMessage("流式上传失败: " + t.getMessage())
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                LogBatchResponse response = LogBatchResponse.newBuilder()
                        .setSuccess(success > 0)
                        .setReceived(received)
                        .setSuccessCount(success)
                        .setFailedCount(failed)
                        .setMessage(String.format("流式上传完成: 接收 %d 条，成功 %d 条，失败 %d 条",
                                received, success, failed))
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();

                log.info("gRPC stream completed: received={}, success={}, failed={}",
                        received, success, failed);
            }
        };
    }

    /**
     * 将 gRPC LogEntry 转换为 Map
     * 支持 google.protobuf.Struct 类型的 extra 字段
     */
    private Map<String, Object> convertToMap(LogEntry entry) {
        Map<String, Object> map = new HashMap<>();

        // ============ 基础字段 ============
        if (!entry.getTraceId().isEmpty()) {
            map.put("traceId", entry.getTraceId());
        }
        if (!entry.getSpanId().isEmpty()) {
            map.put("spanId", entry.getSpanId());
        }
        if (!entry.getTenantId().isEmpty()) {
            map.put("tenantId", entry.getTenantId());
        }
        if (!entry.getSystemId().isEmpty()) {
            map.put("systemId", entry.getSystemId());
        }

        // ============ 时间戳处理 ============
        if (entry.getTimestamp() > 0) {
            map.put("timestamp", entry.getTimestamp());
        } else {
            map.put("timestamp", Instant.now().toEpochMilli());
        }

        // ============ 日志基础信息 ============
        if (!entry.getLevel().isEmpty()) {
            map.put("level", entry.getLevel());
        }
        if (!entry.getLogger().isEmpty()) {
            map.put("logger", entry.getLogger());
        }
        if (!entry.getThread().isEmpty()) {
            map.put("thread", entry.getThread());
        }

        // ============ 代码位置 ============
        if (!entry.getClassName().isEmpty()) {
            map.put("className", entry.getClassName());
        }
        if (!entry.getMethodName().isEmpty()) {
            map.put("methodName", entry.getMethodName());
        }
        if (entry.getLineNumber() > 0) {
            map.put("lineNumber", entry.getLineNumber());
        }

        // ============ 日志内容 ============
        if (!entry.getMessage().isEmpty()) {
            map.put("message", entry.getMessage());
        }
        if (!entry.getException().isEmpty()) {
            map.put("exception", entry.getException());
        }

        // ============ 用户信息 ============
        if (!entry.getUserId().isEmpty()) {
            map.put("userId", entry.getUserId());
        }
        if (!entry.getUserName().isEmpty()) {
            map.put("userName", entry.getUserName());
        }

        // ============ 业务信息 ============
        if (!entry.getModule().isEmpty()) {
            map.put("module", entry.getModule());
        }
        if (!entry.getOperation().isEmpty()) {
            map.put("operation", entry.getOperation());
        }

        // ============ 请求信息 ============
        if (!entry.getRequestUrl().isEmpty()) {
            map.put("requestUrl", entry.getRequestUrl());
        }
        if (!entry.getRequestMethod().isEmpty()) {
            map.put("requestMethod", entry.getRequestMethod());
        }
        if (!entry.getRequestParams().isEmpty()) {
            map.put("requestParams", entry.getRequestParams());
        }
        if (entry.getResponseTime() > 0) {
            map.put("responseTime", entry.getResponseTime());
        }

        // ============ 网络信息 ============
        if (!entry.getIp().isEmpty()) {
            map.put("ip", entry.getIp());
        }
        if (!entry.getUserAgent().isEmpty()) {
            map.put("userAgent", entry.getUserAgent());
        }

        // ============ 标签和扩展字段 ============
        // 标签列表
        if (!entry.getTagsList().isEmpty()) {
            map.put("tags", entry.getTagsList());
        }

        // 扩展字段（google.protobuf.Struct -> Map）
        if (entry.hasExtra()) {
            Map<String, Object> extra = structToMap(entry.getExtra());
            if (!extra.isEmpty()) {
                map.put("extra", extra);
            }
        }

        return map;
    }

    /**
     * 将 google.protobuf.Struct 转换为 Map
     */
    private Map<String, Object> structToMap(Struct struct) {
        if (struct == null || struct.getFieldsCount() == 0) {
            return new HashMap<>();
        }

        Map<String, Object> map = new HashMap<>();
        struct.getFieldsMap().forEach((key, value) -> map.put(key, valueToObject(value)));

        return map;
    }

    /**
     * 将 google.protobuf.Value 转换为 Java 对象
     */
    private Object valueToObject(Value value) {
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
            case KIND_NOT_SET:
            default:
                return null;
        }
    }
}