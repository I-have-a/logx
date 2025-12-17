package com.domidodo.logx.common.validator;

import com.domidodo.logx.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InputValidator 单元测试
 * <p>
 * 测试覆盖：
 * 1. SQL注入防护
 * 2. XSS攻击防护
 * 3. 输入格式验证
 * 4. 边界条件测试
 */
@DisplayName("输入验证器测试")
class InputValidatorTest {

    private InputValidator validator;

    @BeforeEach
    void setUp() {
        validator = new InputValidator();
    }

    // ========== 租户ID验证测试 ==========

    @Test
    @DisplayName("有效的租户ID应该通过验证")
    void testValidTenantId() {
        assertDoesNotThrow(() -> validator.validateTenantId("company_a"));
        assertDoesNotThrow(() -> validator.validateTenantId("tenant-123"));
        assertDoesNotThrow(() -> validator.validateTenantId("TENANT_456"));
    }

    @Test
    @DisplayName("空的租户ID应该抛出异常")
    void testNullTenantId() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> validator.validateTenantId(null)
        );
        assertEquals("租户ID不能为空", exception.getMessage());
    }

    @Test
    @DisplayName("过长的租户ID应该抛出异常")
    void testTooLongTenantId() {
        String longId = "a".repeat(65);
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> validator.validateTenantId(longId)
        );
        assertEquals("租户ID长度不能超过64个字符", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tenant@123",  // 包含@
            "tenant#456",  // 包含#
            "tenant 789",  // 包含空格
            "tenant/123"   // 包含/
    })
    @DisplayName("包含非法字符的租户ID应该抛出异常")
    void testInvalidCharactersInTenantId(String tenantId) {
        assertThrows(BusinessException.class, () -> validator.validateTenantId(tenantId));
    }

    // ========== SQL注入防护测试 ==========

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM users",
            "'; DROP TABLE logs;--",
            "1' OR '1'='1",
            "admin'--",
            "UNION SELECT password FROM users"
    })
    @DisplayName("SQL注入攻击应该被检测")
    void testSqlInjectionDetection(String maliciousInput) {
        assertThrows(
                BusinessException.class,
                () -> validator.validateTenantId(maliciousInput)
        );
    }

    // ========== XSS防护测试 ==========

    @Test
    @DisplayName("XSS攻击向量应该被清理")
    void testXssSanitization() {
        String malicious = "<script>alert('XSS')</script>";
        String sanitized = validator.validateLogMessage(malicious);

        assertFalse(sanitized.contains("<script>"), "应该移除script标签");
        assertTrue(sanitized.contains("&lt;script"), "应该转义HTML标签");
    }

    @Test
    @DisplayName("JavaScript事件处理应该被清理")
    void testJavaScriptEventSanitization() {
        String malicious = "<img src=x onerror='alert(1)'>";
        String sanitized = validator.validateLogMessage(malicious);

        assertFalse(sanitized.contains("onerror="), "应该移除onerror事件");
    }

    @Test
    @DisplayName("正常的HTML内容应该被保留")
    void testNormalContentPreserved() {
        String normal = "这是一条正常的日志消息，包含数字123和符号!@#";
        String sanitized = validator.validateLogMessage(normal);

        assertEquals(normal, sanitized, "正常内容应该保持不变");
    }

    // ========== 查询关键字验证测试 ==========

    @Test
    @DisplayName("正常的查询关键字应该通过验证")
    void testValidQueryKeyword() {
        String keyword = "error message";
        String result = validator.validateQueryKeyword(keyword);
        assertEquals(keyword, result);
    }

    @Test
    @DisplayName("过长的查询关键字应该抛出异常")
    void testTooLongQueryKeyword() {
        String longKeyword = "a".repeat(501);
        assertThrows(
                BusinessException.class,
                () -> validator.validateQueryKeyword(longKeyword)
        );
    }

    @Test
    @DisplayName("包含SQL注入的查询关键字应该被拒绝")
    void testQueryKeywordWithSqlInjection() {
        String malicious = "error' OR '1'='1";
        assertThrows(
                BusinessException.class,
                () -> validator.validateQueryKeyword(malicious)
        );
    }

    // ========== 排序字段验证测试 ==========

    @ParameterizedTest
    @ValueSource(strings = {"timestamp", "level", "module", "operation", "userId"})
    @DisplayName("白名单中的排序字段应该通过验证")
    void testValidSortFields(String field) {
        String result = validator.validateSortField(field);
        assertEquals(field.toLowerCase(), result.toLowerCase());
    }

    @Test
    @DisplayName("不在白名单中的排序字段应该抛出异常")
    void testInvalidSortField() {
        assertThrows(
                BusinessException.class,
                () -> validator.validateSortField("malicious_field")
        );
    }

    @Test
    @DisplayName("包含特殊字符的排序字段应该抛出异常")
    void testSortFieldWithSpecialChars() {
        assertThrows(
                BusinessException.class,
                () -> validator.validateSortField("field; DROP TABLE")
        );
    }

    // ========== 排序方式验证测试 ==========

    @Test
    @DisplayName("有效的排序方式应该通过验证")
    void testValidSortOrder() {
        assertEquals("asc", validator.validateSortOrder("asc"));
        assertEquals("desc", validator.validateSortOrder("desc"));
        assertEquals("asc", validator.validateSortOrder("ASC"));
        assertEquals("desc", validator.validateSortOrder("DESC"));
    }

    @Test
    @DisplayName("无效的排序方式应该抛出异常")
    void testInvalidSortOrder() {
        assertThrows(
                BusinessException.class,
                () -> validator.validateSortOrder("invalid")
        );
    }

    // ========== 分页参数验证测试 ==========

    @Test
    @DisplayName("有效的分页参数应该通过验证")
    void testValidPagination() {
        assertDoesNotThrow(() -> validator.validatePagination(1, 20));
        assertDoesNotThrow(() -> validator.validatePagination(10, 100));
    }

    @Test
    @DisplayName("页码小于1应该抛出异常")
    void testInvalidPageNumber() {
        assertThrows(
                BusinessException.class,
                () -> validator.validatePagination(0, 20)
        );
    }

    @Test
    @DisplayName("每页大小超过限制应该抛出异常")
    void testExcessivePageSize() {
        assertThrows(
                BusinessException.class,
                () -> validator.validatePagination(1, 1001)
        );
    }

    @Test
    @DisplayName("每页大小小于1应该抛出异常")
    void testInvalidPageSize() {
        assertThrows(
                BusinessException.class,
                () -> validator.validatePagination(1, 0)
        );
    }

    // ========== 时间范围验证测试 ==========

    @Test
    @DisplayName("有效的时间范围应该通过验证")
    void testValidTimeRange() {
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();

        assertDoesNotThrow(() -> validator.validateTimeRange(start, end));
    }

    @Test
    @DisplayName("开始时间晚于结束时间应该抛出异常")
    void testInvalidTimeRange() {
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = LocalDateTime.now().minusDays(1);

        assertThrows(
                BusinessException.class,
                () -> validator.validateTimeRange(start, end)
        );
    }

    @Test
    @DisplayName("时间范围超过90天应该抛出异常")
    void testExcessiveTimeRange() {
        LocalDateTime start = LocalDateTime.now().minusDays(91);
        LocalDateTime end = LocalDateTime.now();

        assertThrows(
                BusinessException.class,
                () -> validator.validateTimeRange(start, end)
        );
    }

    @Test
    @DisplayName("时间参数为空应该抛出异常")
    void testNullTimeRange() {
        assertThrows(
                BusinessException.class,
                () -> validator.validateTimeRange(null, LocalDateTime.now())
        );
    }

    // ========== 响应时间范围验证测试 ==========

    @Test
    @DisplayName("有效的响应时间范围应该通过验证")
    void testValidResponseTimeRange() {
        assertDoesNotThrow(() -> validator.validateResponseTimeRange(0L, 1000L));
        assertDoesNotThrow(() -> validator.validateResponseTimeRange(100L, 5000L));
    }

    @Test
    @DisplayName("负数的响应时间应该抛出异常")
    void testNegativeResponseTime() {
        assertThrows(
                BusinessException.class,
                () -> validator.validateResponseTimeRange(-1L, 1000L)
        );
    }

    @Test
    @DisplayName("最小值大于最大值应该抛出异常")
    void testInvalidResponseTimeRange() {
        assertThrows(
                BusinessException.class,
                () -> validator.validateResponseTimeRange(1000L, 100L)
        );
    }

    // ========== 日志消息验证测试 ==========

    @Test
    @DisplayName("正常长度的日志消息应该保持不变")
    void testNormalLogMessage() {
        String message = "这是一条正常的日志消息";
        String result = validator.validateLogMessage(message);
        assertEquals(message, result);
    }

    @Test
    @DisplayName("超长的日志消息应该被截断")
    void testTruncateLongLogMessage() {
        String longMessage = "a".repeat(15000);
        String result = validator.validateLogMessage(longMessage);

        assertTrue(result.length() < longMessage.length(), "超长消息应该被截断");
        assertTrue(result.contains("...[truncated]"), "应该包含截断标记");
    }

    @Test
    @DisplayName("null日志消息应该返回空字符串")
    void testNullLogMessage() {
        String result = validator.validateLogMessage(null);
        assertEquals("", result);
    }
}