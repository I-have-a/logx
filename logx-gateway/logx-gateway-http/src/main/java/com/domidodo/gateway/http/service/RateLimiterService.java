package com.domidodo.gateway.http.service;

import com.domidodo.logx.common.exception.BusinessException;
import com.domidodo.logx.infrastructure.util.RedisRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 限流服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RedisRateLimiter redisRateLimiter;

    /**
     * 系统级别限流：每秒请求数
     */
    @Value("${logx.rate-limit.global.qps:10000}")
    private int globalQps;

    /**
     * 租户级别限流：每秒请求数
     */
    @Value("${logx.rate-limit.tenant.qps:1000}")
    private int tenantQps;

    /**
     * 系统级别限流：每分钟请求数
     */
    @Value("${logx.rate-limit.system.qpm:5000}")
    private int systemQpm;

    /**
     * 限流时间窗口（秒）
     */
    private static final int WINDOW_SECONDS = 60;

    /**
     * 限流Key前缀
     */
    private static final String GLOBAL_KEY_PREFIX = "rate_limit:global:";
    private static final String TENANT_KEY_PREFIX = "rate_limit:tenant:";
    private static final String SYSTEM_KEY_PREFIX = "rate_limit:system:";

    /**
     * 检查全局限流
     *
     * @return true=通过，false=被限流
     */
    public boolean checkGlobalLimit() {
        String key = GLOBAL_KEY_PREFIX + getCurrentMinute();
        boolean allowed = redisRateLimiter.tryAcquire(key, globalQps * 60, WINDOW_SECONDS);

        if (!allowed) {
            log.warn("已超出全局速率限制，当前分钟数：{}", getCurrentMinute());
        }

        return allowed;
    }

    /**
     * 检查租户级限流
     *
     * @param tenantId 租户ID
     * @return true=通过，false=被限流
     */
    public boolean checkTenantLimit(String tenantId) {
        String key = TENANT_KEY_PREFIX + tenantId + ":" + getCurrentMinute();
        boolean allowed = redisRateLimiter.tryAcquire(key, tenantQps * 60, WINDOW_SECONDS);

        if (!allowed) {
            log.warn("超出租户费率限制，租户ID:{}，分钟：{}", tenantId, getCurrentMinute());
        }

        return allowed;
    }

    /**
     * 检查系统级限流
     *
     * @param tenantId 租户ID
     * @param systemId 系统ID
     * @return true=通过，false=被限流
     */
    public boolean checkSystemLimit(String tenantId, String systemId) {
        String key = SYSTEM_KEY_PREFIX + tenantId + ":" + systemId + ":" + getCurrentMinute();
        boolean allowed = redisRateLimiter.tryAcquire(key, systemQpm, WINDOW_SECONDS);

        if (!allowed) {
            log.warn("超出系统速率限制，tenantId:{}，systemId:｛}，分钟：{}",
                    tenantId, systemId, getCurrentMinute());
        }

        return allowed;
    }

    /**
     * 综合检查限流（按优先级检查）
     *
     * @param tenantId 租户ID
     * @param systemId 系统ID
     * @throws BusinessException 限流异常
     */
    public void checkRateLimit(String tenantId, String systemId) {
        // 1. 全局限流检查
        if (!checkGlobalLimit()) {
            throw new BusinessException("系统繁忙，请稍后重试");
        }

        // 2. 租户限流检查
        if (!checkTenantLimit(tenantId)) {
            throw new BusinessException("租户请求过于频繁，请稍后重试");
        }

        // 3. 系统限流检查
        if (!checkSystemLimit(tenantId, systemId)) {
            throw new BusinessException("系统请求过于频繁，请稍后重试");
        }
    }

    /**
     * 获取当前分钟（格式：yyyyMMddHHmm）
     */
    private String getCurrentMinute() {
        long currentTimeMillis = System.currentTimeMillis();
        return String.valueOf(currentTimeMillis / 60000);
    }

    /**
     * 重置租户限流计数
     *
     * @param tenantId 租户ID
     */
    public void resetTenantLimit(String tenantId) {
        String key = TENANT_KEY_PREFIX + tenantId + ":" + getCurrentMinute();
        redisRateLimiter.reset(key);
        log.info("Reset tenant rate limit, tenantId: {}", tenantId);
    }

    /**
     * 获取租户剩余配额
     *
     * @param tenantId 租户ID
     * @return 剩余请求次数
     */
    public long getTenantRemaining(String tenantId) {
        String key = TENANT_KEY_PREFIX + tenantId + ":" + getCurrentMinute();
        return redisRateLimiter.getRemaining(key, tenantQps * 60);
    }
}