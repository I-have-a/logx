package com.domidodo.logx.gateway.grpc.interceptor;

import com.domidodo.logx.common.constant.SystemConstant;
import com.domidodo.logx.common.context.TenantContext;
import com.domidodo.logx.gateway.grpc.mapper.ValidateMapper;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * gRPC 认证拦截器
 * 验证 API Key 和提取租户信息
 */
@Slf4j
@Component
@GrpcGlobalServerInterceptor
public class GrpcAuthInterceptor implements ServerInterceptor {

    @Autowired
    ValidateMapper validateMapper;

    /**
     * 元数据 Key
     */
    private static final Metadata.Key<String> API_KEY_METADATA_KEY =
            Metadata.Key.of("X-Api-Key", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> TENANT_ID_METADATA_KEY =
            Metadata.Key.of("X-Tenant-Id", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> SYSTEM_ID_METADATA_KEY =
            Metadata.Key.of("X-System-Id", Metadata.ASCII_STRING_MARSHALLER);
    @Qualifier("redisTemplate")
    @Autowired
    private RedisTemplate redisTemplate;

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

            // 3. 验证 API Key
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
     * 验证 API Key
     */
    private boolean validateApiKey(String apiKey, String tenantId, String systemId) {
        log.debug("验证 API Key：apiKey={}，tenantId={}，systemId={}", apiKey, tenantId, systemId);
        if (redisTemplate.opsForValue().get(SystemConstant.REDIS_KEY_API_KEY + tenantId + ":" + systemId + ":" + apiKey) != null) {
            return true;
        }
        TenantContext.setIgnoreTenant(true);
        boolean b = validateMapper.validateApiKey(apiKey, tenantId, systemId) >= 1;
        if (b) {
            redisTemplate.opsForValue().set(SystemConstant.REDIS_KEY_API_KEY + tenantId + ":" + systemId + ":" + apiKey,
                    "1",
                    SystemConstant.LOG_RETENTION_DAYS, TimeUnit.DAYS);
        }
        TenantContext.setIgnoreTenant(false);
        return b;

    }
}