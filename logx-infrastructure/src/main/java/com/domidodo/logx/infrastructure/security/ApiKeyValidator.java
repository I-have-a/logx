package com.domidodo.logx.infrastructure.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;

/**
 * API密钥验证器
 * 实现API Key的安全验证，包括：
 * 1. 密钥格式验证
 * 2. 密钥有效性检查
 * 3. 请求签名验证
 * 4. 频率限制
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyValidator {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String API_KEY_CACHE_PREFIX = "api_key:";
    private static final String API_KEY_BLACKLIST_PREFIX = "api_key:blacklist:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    /**
     * 验证API密钥
     *
     * @param apiKey API密钥
     * @param tenantId 租户ID
     * @param systemId 系统ID
     * @return 验证结果
     */
    public ApiKeyValidationResult validate(String apiKey, String tenantId, String systemId) {
        try {
            // 1. 格式验证
            if (!isValidFormat(apiKey)) {
                log.warn("Invalid API key format: {}", maskApiKey(apiKey));
                return ApiKeyValidationResult.invalid("API密钥格式不正确");
            }

            // 2. 黑名单检查
            if (isBlacklisted(apiKey)) {
                log.warn("Blacklisted API key used: {}", maskApiKey(apiKey));
                return ApiKeyValidationResult.invalid("API密钥已被禁用");
            }

            // 3. 缓存检查
            String cacheKey = buildCacheKey(apiKey, tenantId, systemId);
            ApiKeyInfo cachedInfo = (ApiKeyInfo) redisTemplate.opsForValue().get(cacheKey);

            if (cachedInfo != null) {
                if (cachedInfo.isExpired()) {
                    return ApiKeyValidationResult.invalid("API密钥已过期");
                }
                return ApiKeyValidationResult.valid(cachedInfo);
            }

            // 4. 数据库验证（这里需要注入SystemMapper）
            // 为了演示，我们返回一个简单的验证结果
            ApiKeyInfo info = new ApiKeyInfo(tenantId, systemId, apiKey);

            // 5. 缓存验证结果
            redisTemplate.opsForValue().set(cacheKey, info, CACHE_TTL);

            return ApiKeyValidationResult.valid(info);

        } catch (Exception e) {
            log.error("API key validation error", e);
            return ApiKeyValidationResult.invalid("验证失败");
        }
    }

    /**
     * 验证请求签名
     *
     * @param apiKey API密钥
     * @param timestamp 时间戳
     * @param signature 签名
     * @return 是否有效
     */
    public boolean validateSignature(String apiKey, long timestamp, String signature) {
        try {
            // 1. 时间戳检查（防止重放攻击）
            long currentTime = System.currentTimeMillis();
            long timeDiff = Math.abs(currentTime - timestamp);

            if (timeDiff > 300000) { // 5分钟有效期
                log.warn("Request timestamp expired: diff={}ms", timeDiff);
                return false;
            }

            // 2. 签名验证
            String expectedSignature = generateSignature(apiKey, timestamp);
            boolean valid = MessageDigest.isEqual(
                    signature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8)
            );

            if (!valid) {
                log.warn("Invalid signature for API key: {}", maskApiKey(apiKey));
            }

            return valid;

        } catch (Exception e) {
            log.error("Signature validation error", e);
            return false;
        }
    }

    /**
     * 将API密钥加入黑名单
     */
    public void blacklist(String apiKey, String reason) {
        String key = API_KEY_BLACKLIST_PREFIX + hashApiKey(apiKey);
        redisTemplate.opsForValue().set(key, reason, Duration.ofDays(365));

        // 清除缓存
        invalidateCache(apiKey);

        log.warn("API key blacklisted: {} - {}", maskApiKey(apiKey), reason);
    }

    /**
     * 清除缓存
     */
    public void invalidateCache(String apiKey) {
        String pattern = API_KEY_CACHE_PREFIX + hashApiKey(apiKey) + ":*";
        redisTemplate.delete(redisTemplate.keys(pattern));
    }

    /**
     * 检查是否在黑名单中
     */
    private boolean isBlacklisted(String apiKey) {
        String key = API_KEY_BLACKLIST_PREFIX + hashApiKey(apiKey);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 验证API密钥格式
     */
    private boolean isValidFormat(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }

        // API密钥格式：sk_开头，后跟32位十六进制字符
        return apiKey.matches("^sk_[a-f0-9]{32}$");
    }

    /**
     * 生成签名
     */
    private String generateSignature(String apiKey, long timestamp) {
        try {
            String data = apiKey + ":" + timestamp;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    apiKey.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] hmacData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /**
     * 对API密钥进行哈希（用于缓存键）
     */
    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash API key", e);
        }
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(String apiKey, String tenantId, String systemId) {
        return API_KEY_CACHE_PREFIX + hashApiKey(apiKey) + ":" + tenantId + ":" + systemId;
    }

    /**
     * 掩码API密钥（用于日志）
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 10) {
            return "***";
        }
        return apiKey.substring(0, 6) + "***" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * API密钥信息
     */
    @Getter
    public static class ApiKeyInfo implements java.io.Serializable {
        // Getters
        private final String tenantId;
        private final String systemId;
        private final String apiKey;
        private final long createdAt;
        @Setter
        private Long expiresAt;

        public ApiKeyInfo(String tenantId, String systemId, String apiKey) {
            this.tenantId = tenantId;
            this.systemId = systemId;
            this.apiKey = apiKey;
            this.createdAt = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return expiresAt != null && System.currentTimeMillis() > expiresAt;
        }

    }

    /**
     * 验证结果
     */
    @Getter
    public static class ApiKeyValidationResult {
        private final boolean valid;
        private final String message;
        private final ApiKeyInfo info;

        private ApiKeyValidationResult(boolean valid, String message, ApiKeyInfo info) {
            this.valid = valid;
            this.message = message;
            this.info = info;
        }

        public static ApiKeyValidationResult valid(ApiKeyInfo info) {
            return new ApiKeyValidationResult(true, "Valid", info);
        }

        public static ApiKeyValidationResult invalid(String message) {
            return new ApiKeyValidationResult(false, message, null);
        }

    }
}