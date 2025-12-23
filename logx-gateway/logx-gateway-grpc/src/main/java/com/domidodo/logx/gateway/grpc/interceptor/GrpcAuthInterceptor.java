package com.domidodo.logx.gateway.grpc.interceptor;

import com.domidodo.logx.common.context.TenantContext;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.stereotype.Component;

/**
 * gRPC 认证拦截器
 * 验证 API Key 和提取租户信息
 */
@Slf4j
@Component
@GrpcGlobalServerInterceptor
public class GrpcAuthInterceptor implements ServerInterceptor {

    /**
     * 元数据 Key
     */
    private static final Metadata.Key<String> API_KEY_METADATA_KEY =
            Metadata.Key.of("X-Api-Key", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> TENANT_ID_METADATA_KEY =
            Metadata.Key.of("X-Tenant-Id", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> SYSTEM_ID_METADATA_KEY =
            Metadata.Key.of("X-System-Id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        try {
            // 1. 提取认证信息
            String apiKey = headers.get(API_KEY_METADATA_KEY);
            String tenantId = headers.get(TENANT_ID_METADATA_KEY);
            String systemId = headers.get(SYSTEM_ID_METADATA_KEY);

            // 2. 验证必填字段
            if (apiKey == null || apiKey.isEmpty()) {
                call.close(Status.UNAUTHENTICATED.withDescription("Missing API Key"), headers);
                return new ServerCall.Listener<>() {
                };
            }

            if (tenantId == null || tenantId.isEmpty()) {
                call.close(Status.UNAUTHENTICATED.withDescription("Missing Tenant ID"), headers);
                return new ServerCall.Listener<>() {
                };
            }

            if (systemId == null || systemId.isEmpty()) {
                call.close(Status.UNAUTHENTICATED.withDescription("Missing System ID"), headers);
                return new ServerCall.Listener<>() {
                };
            }

            // 3. 验证 API Key（TODO: 实际应该查询数据库验证）
            if (!validateApiKey(apiKey, tenantId, systemId)) {
                call.close(Status.PERMISSION_DENIED.withDescription("Invalid API Key"), headers);
                return new ServerCall.Listener<>() {
                };
            }

            // 4. 设置租户上下文
            TenantContext.setTenantId(tenantId);
            TenantContext.setSystemId(systemId);

            log.debug("gRPC请求已通过身份验证：tenantId={}，systemId={}", tenantId, systemId);

            // 5. 继续处理请求，并在完成后清理上下文
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(
                    next.startCall(call, headers)) {
                @Override
                public void onComplete() {
                    try {
                        super.onComplete();
                    } finally {
                        TenantContext.clear();
                    }
                }

                @Override
                public void onCancel() {
                    try {
                        super.onCancel();
                    } finally {
                        TenantContext.clear();
                    }
                }
            };

        } catch (Exception e) {
            log.error("Authentication error", e);
            call.close(Status.INTERNAL.withDescription("Authentication failed"), headers);
            return new ServerCall.Listener<>() {
            };
        }
    }

    /**
     * 验证 API Key（TODO: 实现实际的验证逻辑）
     */
    private boolean validateApiKey(String apiKey, String tenantId, String systemId) {
        // 这里应该查询数据库验证 API Key 是否有效
        // 暂时简单返回 true
        return true;
    }
}