package com.domidodo.logx.gateway.grpc.interceptor;

import com.domidodo.logx.common.context.TenantContext;
import com.domidodo.logx.infrastructure.util.RedisRateLimiter;
import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * gRPC 限流拦截器
 * 基于 Redis 实现三级限流
 */
@Slf4j
@Component
@GrpcGlobalServerInterceptor
@Order(100) // 在认证拦截器之后执行
@RequiredArgsConstructor
public class GrpcRateLimitInterceptor implements ServerInterceptor {

    private final RedisRateLimiter redisRateLimiter;

    @Value("${logx.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${logx.rate-limit.global.qps:10000}")
    private int globalQps;

    @Value("${logx.rate-limit.tenant.qps:1000}")
    private int tenantQps;

    @Value("${logx.rate-limit.system.qpm:5000}")
    private int systemQpm;

    private static final int WINDOW_SECONDS = 60;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        // 如果限流未启用，直接放行
        if (!rateLimitEnabled) {
            return next.startCall(call, headers);
        }

        try {
            Long tenantId = TenantContext.getTenantId();
            Long systemId = TenantContext.getSystemId();

            // 1. 全局限流检查
            if (!checkGlobalLimit()) {
                log.warn("Global rate limit exceeded");
                call.close(Status.RESOURCE_EXHAUSTED
                        .withDescription("系统繁忙，请稍后重试"), headers);
                return new ServerCall.Listener<>() {
                };
            }

            // 2. 租户限流检查
            if (tenantId != null && !checkTenantLimit(tenantId)) {
                log.warn("Tenant rate limit exceeded: tenantId={}", tenantId);
                call.close(Status.RESOURCE_EXHAUSTED
                        .withDescription("租户请求过于频繁，请稍后重试"), headers);
                return new ServerCall.Listener<>() {
                };
            }

            // 3. 系统限流检查
            if (systemId != null && !checkSystemLimit(tenantId, systemId)) {
                log.warn("System rate limit exceeded: tenantId={}, systemId={}", tenantId, systemId);
                call.close(Status.RESOURCE_EXHAUSTED
                        .withDescription("系统请求过于频繁，请稍后重试"), headers);
                return new ServerCall.Listener<>() {
                };
            }

            // 通过限流检查，继续处理
            return next.startCall(call, headers);

        } catch (Exception e) {
            log.error("Rate limit check error", e);
            // 异常情况放行，避免服务不可用
            return next.startCall(call, headers);
        }
    }

    /**
     * 全局限流检查
     */
    private boolean checkGlobalLimit() {
        String key = "rate_limit:global:" + getCurrentMinute();
        return redisRateLimiter.tryAcquire(key, globalQps * 60, WINDOW_SECONDS);
    }

    /**
     * 租户限流检查
     */
    private boolean checkTenantLimit(Long tenantId) {
        String key = "rate_limit:tenant:" + tenantId + ":" + getCurrentMinute();
        return redisRateLimiter.tryAcquire(key, tenantQps * 60, WINDOW_SECONDS);
    }

    /**
     * 系统限流检查
     */
    private boolean checkSystemLimit(Long tenantId, Long systemId) {
        String key = "rate_limit:system:" + tenantId + ":" + systemId + ":" + getCurrentMinute();
        return redisRateLimiter.tryAcquire(key, systemQpm, WINDOW_SECONDS);
    }

    /**
     * 获取当前分钟
     */
    private String getCurrentMinute() {
        return String.valueOf(System.currentTimeMillis() / 60000);
    }
}