package com.domidodo.logx.gateway.grpc.service;

import com.domidodo.logx.common.context.TenantContext;
import com.domidodo.logx.common.grpc.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * gRPC 日志接收服务
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class LogIngestGrpcService extends LogServiceGrpc.LogServiceImplBase {

    private final KafkaLogSender kafkaLogSender;

    @Value("${logx.batch.max-size:100}")
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
    public StreamObserver<LogEntry> streamLogs(StreamObserver<LogBatchResponse> responseObserver) {
        return new StreamObserver<>() {
            private int received = 0;
            private int success = 0;
            private int failed = 0;

            @Override
            public void onNext(LogEntry logEntry) {
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
     */
    private Map<String, Object> convertToMap(LogEntry entry) {
        Map<String, Object> map = new HashMap<>();

        // 基础字段
        map.put("traceId", entry.getTraceId());
        map.put("spanId", entry.getSpanId());
        map.put("tenantId", entry.getTenantId());
        map.put("systemId", entry.getSystemId());

        // 时间戳处理
        if (entry.getTimestamp() > 0) {
            map.put("timestamp", entry.getTimestamp());
        } else {
            map.put("timestamp", Instant.now().toEpochMilli());
        }

        // 日志信息
        map.put("level", entry.getLevel());
        map.put("logger", entry.getLogger());
        map.put("thread", entry.getThread());
        map.put("className", entry.getClassName());
        map.put("methodName", entry.getMethodName());
        map.put("lineNumber", entry.getLineNumber());
        map.put("message", entry.getMessage());
        map.put("exception", entry.getException());

        // 用户信息
        map.put("userId", entry.getUserId());
        map.put("userName", entry.getUserName());

        // 业务信息
        map.put("module", entry.getModule());
        map.put("operation", entry.getOperation());

        // 请求信息
        map.put("requestUrl", entry.getRequestUrl());
        map.put("requestMethod", entry.getRequestMethod());
        map.put("requestParams", entry.getRequestParams());
        map.put("responseTime", entry.getResponseTime());

        // 网络信息
        map.put("ip", entry.getIp());
        map.put("userAgent", entry.getUserAgent());

        // 标签和扩展字段
        if (!entry.getTagsList().isEmpty()) {
            map.put("tags", entry.getTagsList());
        }
        map.put("extra", entry.getExtra());

        return map;
    }
}