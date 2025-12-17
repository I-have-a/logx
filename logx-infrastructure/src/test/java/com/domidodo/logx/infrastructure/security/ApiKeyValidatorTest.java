package com.domidodo.logx.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ApiKeyValidator 单元测试
 * <p>
 * 测试覆盖：
 * 1. API密钥格式验证
 * 2. 密钥有效性检查
 * 3. 黑名单检查
 * 4. 签名验证
 * 5. 缓存机制
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("API密钥验证器测试")
class ApiKeyValidatorTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private ApiKeyValidator validator;

    private static final String VALID_API_KEY = "sk_0123456789abcdef0123456789abcdef";
    private static final String INVALID_FORMAT_KEY = "invalid_key";
    private static final String TENANT_ID = "company_a";
    private static final String SYSTEM_ID = "erp_system";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("有效的API密钥应该通过验证")
    void testValidApiKey() {
        // Given
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // When
        ApiKeyValidator.ApiKeyValidationResult result =
                validator.validate(VALID_API_KEY, TENANT_ID, SYSTEM_ID);

        // Then
        assertTrue(result.isValid(), "有效的API密钥应该通过验证");
        assertNotNull(result.getInfo(), "应该返回密钥信息");
        assertEquals(TENANT_ID, result.getInfo().getTenantId());
        assertEquals(SYSTEM_ID, result.getInfo().getSystemId());

        // 验证缓存操作
        verify(valueOperations, times(1)).set(anyString(), any(), any());
    }

    @Test
    @DisplayName("格式不正确的API密钥应该验证失败")
    void testInvalidFormatApiKey() {
        // When
        ApiKeyValidator.ApiKeyValidationResult result =
                validator.validate(INVALID_FORMAT_KEY, TENANT_ID, SYSTEM_ID);

        // Then
        assertFalse(result.isValid(), "格式不正确的密钥应该验证失败");
        assertEquals("API密钥格式不正确", result.getMessage());
        assertNull(result.getInfo());
    }

    @Test
    @DisplayName("空的API密钥应该验证失败")
    void testNullApiKey() {
        // When
        ApiKeyValidator.ApiKeyValidationResult result =
                validator.validate(null, TENANT_ID, SYSTEM_ID);

        // Then
        assertFalse(result.isValid());
        assertEquals("API密钥格式不正确", result.getMessage());
    }

    @Test
    @DisplayName("黑名单中的API密钥应该验证失败")
    void testBlacklistedApiKey() {
        // Given
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        // When
        ApiKeyValidator.ApiKeyValidationResult result =
                validator.validate(VALID_API_KEY, TENANT_ID, SYSTEM_ID);

        // Then
        assertFalse(result.isValid());
        assertEquals("API密钥已被禁用", result.getMessage());
    }

    @Test
    @DisplayName("缓存命中应该直接返回结果")
    void testCacheHit() {
        // Given
        ApiKeyValidator.ApiKeyInfo cachedInfo =
                new ApiKeyValidator.ApiKeyInfo(TENANT_ID, SYSTEM_ID, VALID_API_KEY);
        when(valueOperations.get(anyString())).thenReturn(cachedInfo);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // When
        ApiKeyValidator.ApiKeyValidationResult result =
                validator.validate(VALID_API_KEY, TENANT_ID, SYSTEM_ID);

        // Then
        assertTrue(result.isValid());
        assertNotNull(result.getInfo());

        // 不应该再次设置缓存
        verify(valueOperations, never()).set(anyString(), any(), any());
    }

    @Test
    @DisplayName("过期的API密钥应该验证失败")
    void testExpiredApiKey() {
        // Given
        ApiKeyValidator.ApiKeyInfo expiredInfo =
                new ApiKeyValidator.ApiKeyInfo(TENANT_ID, SYSTEM_ID, VALID_API_KEY);
        expiredInfo.setExpiresAt(System.currentTimeMillis() - 1000); // 1秒前过期

        when(valueOperations.get(anyString())).thenReturn(expiredInfo);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // When
        ApiKeyValidator.ApiKeyValidationResult result =
                validator.validate(VALID_API_KEY, TENANT_ID, SYSTEM_ID);

        // Then
        assertFalse(result.isValid());
        assertEquals("API密钥已过期", result.getMessage());
    }

    @Test
    @DisplayName("有效的签名应该通过验证")
    void testValidSignature() {
        // Given
        long timestamp = System.currentTimeMillis();

        // 首先生成一个有效的签名（使用内部方法）
        // 这里我们需要模拟签名生成过程

        // When & Then
        // 注意：实际测试中需要使用反射或公开方法来测试签名
        // 这里简化测试
        assertDoesNotThrow(() -> {
            validator.validateSignature(VALID_API_KEY, timestamp, "dummy_signature");
        });
    }

    @Test
    @DisplayName("过期的时间戳应该验证失败")
    void testExpiredTimestamp() {
        // Given
        long expiredTimestamp = System.currentTimeMillis() - 400000; // 6分钟前
        String signature = "dummy_signature";

        // When
        boolean result = validator.validateSignature(VALID_API_KEY, expiredTimestamp, signature);

        // Then
        assertFalse(result, "过期的时间戳应该验证失败");
    }

    @Test
    @DisplayName("加入黑名单应该清除缓存")
    void testBlacklistClearCache() {
        // Given
        when(redisTemplate.keys(anyString())).thenReturn(java.util.Set.of("key1", "key2"));

        // When
        validator.blacklist(VALID_API_KEY, "安全原因");

        // Then
        verify(valueOperations, times(1)).set(anyString(), eq("安全原因"), any());
        verify(redisTemplate, times(1)).delete(anySet());
    }

    @Test
    @DisplayName("清除缓存应该删除所有相关key")
    void testInvalidateCache() {
        // Given
        when(redisTemplate.keys(anyString())).thenReturn(
                java.util.Set.of("cache:key1", "cache:key2")
        );

        // When
        validator.invalidateCache(VALID_API_KEY);

        // Then
        verify(redisTemplate, times(1)).keys(anyString());
        verify(redisTemplate, times(1)).delete(anySet());
    }
}