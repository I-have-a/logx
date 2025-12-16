package com.domidodo.logx.infrastructure.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 限流工具类
 * 基于令牌桶算法实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Lua脚本：令牌桶算法
     * 返回值：1=允许通过，0=限流
     */
    private static final String LUA_SCRIPT =
            "local key = KEYS[1]\n" +
            "local limit = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local current = tonumber(redis.call('get', key) or '0')\n" +
            "if current < limit then\n" +
            "    redis.call('incr', key)\n" +
            "    if current == 0 then\n" +
            "        redis.call('expire', key, window)\n" +
            "    end\n" +
            "    return 1\n" +
            "else\n" +
            "    return 0\n" +
            "end";

    /**
     * 检查是否允许通过（简单计数器方式）
     *
     * @param key    限流key
     * @param limit  限流次数
     * @param window 时间窗口（秒）
     * @return true=允许，false=拒绝
     */
    public boolean tryAcquire(String key, int limit, int window) {
        try {
            RedisScript<Long> script = RedisScript.of(LUA_SCRIPT, Long.class);
            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    String.valueOf(limit),
                    String.valueOf(window)
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("Rate limiter error for key: {}, allowing request", key, e);
            return true; // 异常情况放行，避免服务不可用
        }
    }

    /**
     * 获取剩余配额
     *
     * @param key    限流key
     * @param limit  限流次数
     * @return 剩余次数
     */
    public long getRemaining(String key, int limit) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            long current = value != null ? Long.parseLong(value.toString()) : 0;
            return Math.max(0, limit - current);
        } catch (Exception e) {
            log.error("Get remaining quota error for key: {}", key, e);
            return limit;
        }
    }

    /**
     * 获取key的过期时间
     *
     * @param key 限流key
     * @return 剩余秒数
     */
    public long getTTL(String key) {
        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? ttl : 0;
        } catch (Exception e) {
            log.error("Get TTL error for key: {}", key, e);
            return 0;
        }
    }

    /**
     * 重置限流计数器
     *
     * @param key 限流key
     */
    public void reset(String key) {
        try {
            redisTemplate.delete(key);
            log.info("Reset rate limiter for key: {}", key);
        } catch (Exception e) {
            log.error("Reset rate limiter error for key: {}", key, e);
        }
    }
}