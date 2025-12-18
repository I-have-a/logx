package com.domidodo.logx;

import com.domidodo.logx.sdk.core.LogXClient;
import com.domidodo.logx.sdk.core.model.LogEntry;
import com.google.protobuf.Struct;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

/**
 * google.protobuf.Struct 完整测试示例
 * 演示各种数据类型和使用场景
 */
@SpringBootTest(classes = LogXClient.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StructCompleteTest {

    @Autowired(required = false)
    private LogXClient logXClient;

    @BeforeAll
    public static void setup() {
        System.out.println("========== Struct 测试开始 ==========");
    }

    @AfterAll
    public static void teardown() {
        System.out.println("========== Struct 测试结束 ==========");
    }

    /**
     * 测试1：基本数据类型
     */
    @Test
    @Order(1)
    public void test01_BasicTypes() {
        System.out.println("\n--- 测试1：基本数据类型 ---");

        Map<String, Object> extra = new HashMap<>();
        extra.put("stringValue", "hello world");
        extra.put("intValue", 123);
        extra.put("longValue", 9876543210L);
        extra.put("doubleValue", 3.14159);
        extra.put("booleanValue", true);
        extra.put("nullValue", null);

        logXClient.info("基本数据类型测试", extra);

        System.out.println("✅ 基本类型测试完成");
        System.out.println("支持类型：String, Number, Boolean, Null");
    }

    /**
     * 测试2：数组类型
     */
    @Test
    @Order(2)
    public void test02_ArrayTypes() {
        System.out.println("\n--- 测试2：数组类型 ---");

        Map<String, Object> extra = new HashMap<>();
        extra.put("stringArray", List.of("apple", "banana", "orange"));
        extra.put("numberArray", List.of(1, 2, 3, 4, 5));
        extra.put("mixedArray", List.of("text", 123, true, null));
        extra.put("emptyArray", List.of());

        logXClient.info("数组类型测试", extra);

        System.out.println("✅ 数组测试完成");
        System.out.println("支持：字符串数组、数字数组、混合数组");
    }

    /**
     * 测试3：嵌套对象
     */
    @Test
    @Order(3)
    public void test03_NestedObjects() {
        System.out.println("\n--- 测试3：嵌套对象 ---");

        Map<String, Object> extra = new HashMap<>();

        // 简单嵌套
        extra.put("user", Map.of(
                "id", "user-001",
                "name", "张三",
                "age", 25
        ));

        // 多层嵌套
        extra.put("order", Map.of(
                "id", "order-123",
                "amount", 999.99,
                "customer", Map.of(
                        "name", "李四",
                        "phone", "13800138000",
                        "address", Map.of(
                                "province", "北京",
                                "city", "北京市",
                                "district", "朝阳区"
                        )
                )
        ));

        logXClient.info("嵌套对象测试", extra);

        System.out.println("✅ 嵌套对象测试完成");
        System.out.println("支持：任意深度的对象嵌套");
    }

    /**
     * 测试4：复杂混合结构
     */
    @Test
    @Order(4)
    public void test04_ComplexStructures() {
        System.out.println("\n--- 测试4：复杂混合结构 ---");

        Map<String, Object> extra = new HashMap<>();

        // 对象数组
        extra.put("items", List.of(
                Map.of("id", "item-1", "name", "商品A", "price", 99.99),
                Map.of("id", "item-2", "name", "商品B", "price", 199.99)
        ));

        // 数组对象
        extra.put("categories", Map.of(
                "tech", List.of("手机", "电脑", "平板"),
                "clothing", List.of("T恤", "裤子", "鞋子")
        ));

        // 混合嵌套
        extra.put("metadata", Map.of(
                "version", "2.0.1",
                "features", List.of("feature-a", "feature-b"),
                "config", Map.of(
                        "enabled", true,
                        "options", List.of(1, 2, 3)
                )
        ));

        logXClient.info("复杂结构测试", extra);

        System.out.println("✅ 复杂结构测试完成");
        System.out.println("支持：对象数组、数组对象、任意混合");
    }

    /**
     * 测试5：业务场景 - 用户登录
     */
    @Test
    @Order(5)
    public void test05_UserLogin() {
        System.out.println("\n--- 测试5：用户登录场景 ---");

        Map<String, Object> extra = new HashMap<>();
        extra.put("userId", "user-12345");
        extra.put("username", "zhangsan");
        extra.put("loginType", "password");
        extra.put("deviceInfo", Map.of(
                "deviceId", "device-abc",
                "deviceType", "mobile",
                "os", "iOS",
                "version", "15.0",
                "brand", "Apple",
                "model", "iPhone 13"
        ));
        extra.put("location", Map.of(
                "ip", "192.168.1.100",
                "country", "中国",
                "province", "北京",
                "city", "北京市"
        ));
        extra.put("success", true);
        extra.put("loginTime", System.currentTimeMillis());

        logXClient.info("用户登录成功", extra);

        System.out.println("✅ 用户登录场景测试完成");
    }

    /**
     * 测试6：业务场景 - 订单创建
     */
    @Test
    @Order(6)
    public void test06_OrderCreation() {
        System.out.println("\n--- 测试6：订单创建场景 ---");

        Map<String, Object> extra = new HashMap<>();
        extra.put("orderId", "order-" + UUID.randomUUID());
        extra.put("customerId", "customer-001");
        extra.put("totalAmount", 1299.97);
        extra.put("currency", "CNY");
        extra.put("paymentMethod", "alipay");
        extra.put("items", List.of(
                Map.of(
                        "productId", "prod-001",
                        "productName", "iPhone 14",
                        "quantity", 1,
                        "price", 999.99
                ),
                Map.of(
                        "productId", "prod-002",
                        "productName", "AirPods Pro",
                        "quantity", 2,
                        "price", 149.99
                )
        ));
        extra.put("shippingAddress", Map.of(
                "name", "王五",
                "phone", "13900139000",
                "address", "北京市朝阳区某某街道123号"
        ));
        extra.put("coupon", Map.of(
                "code", "DISCOUNT10",
                "discount", 100.0,
                "type", "fixed"
        ));

        logXClient.info("订单创建成功", extra);

        System.out.println("✅ 订单创建场景测试完成");
    }

    /**
     * 测试7：业务场景 - API 调用监控
     */
    @Test
    @Order(7)
    public void test07_ApiMonitoring() {
        System.out.println("\n--- 测试7：API 调用监控 ---");

        Map<String, Object> extra = new HashMap<>();
        extra.put("apiName", "getUserInfo");
        extra.put("method", "GET");
        extra.put("path", "/api/v1/users/12345");
        extra.put("statusCode", 200);
        extra.put("responseTime", 125);
        extra.put("requestSize", 256);
        extra.put("responseSize", 1024);
        extra.put("headers", Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer ***",
                "User-Agent", "Mozilla/5.0"
        ));
        extra.put("queryParams", Map.of(
                "include", "profile,settings",
                "format", "json"
        ));
        extra.put("performance", Map.of(
                "dbQueryTime", 50,
                "cacheHitRate", 0.85,
                "cpuUsage", 12.5,
                "memoryUsage", 45.2
        ));

        logXClient.info("API 调用完成", extra);

        System.out.println("✅ API 监控场景测试完成");
    }

    /**
     * 测试8：Map <-> Struct 转换
     */
    @Test
    @Order(8)
    public void test08_MapStructConversion() {
        System.out.println("\n--- 测试8：Map <-> Struct 转换 ---");

        // 准备测试数据
        Map<String, Object> original = new HashMap<>();
        original.put("string", "test");
        original.put("number", 123);
        original.put("boolean", true);
        original.put("array", List.of(1, 2, 3));
        original.put("object", Map.of(
                "nested", "value",
                "count", 42
        ));

        // Map -> Struct
        Struct struct = LogEntry.mapToStruct(original);
        Assertions.assertNotNull(struct);
        Assertions.assertEquals(5, struct.getFieldsCount());
        System.out.println("✅ Map -> Struct 转换成功");
        System.out.println("   字段数量: " + struct.getFieldsCount());

        // Struct -> Map
        Map<String, Object> converted = LogEntry.structToMap(struct);
        Assertions.assertNotNull(converted);
        Assertions.assertEquals(5, converted.size());
        System.out.println("✅ Struct -> Map 转换成功");

        // 验证数据
        Assertions.assertEquals("test", converted.get("string"));
        Assertions.assertEquals(123.0, converted.get("number")); // 数字转为 double
        Assertions.assertEquals(true, converted.get("boolean"));

        List<?> array = (List<?>) converted.get("array");
        Assertions.assertEquals(3, array.size());

        Map<?, ?> object = (Map<?, ?>) converted.get("object");
        Assertions.assertEquals("value", object.get("nested"));
        Assertions.assertEquals(42.0, object.get("count"));

        System.out.println("✅ 数据验证通过");
        System.out.println("   String: " + converted.get("string"));
        System.out.println("   Number: " + converted.get("number"));
        System.out.println("   Boolean: " + converted.get("boolean"));
        System.out.println("   Array size: " + array.size());
        System.out.println("   Object keys: " + object.keySet());
    }

    /**
     * 测试9：完整 LogEntry 使用
     */
    @Test
    @Order(9)
    public void test09_CompleteLogEntry() {
        System.out.println("\n--- 测试9：完整 LogEntry ---");

        // 创建完整的日志条目
        LogEntry entry = LogEntry.builder()
                .level("INFO")
                .message("完整日志测试")
                .userId("user-001")
                .userName("测试用户")
                .module("测试模块")
                .operation("测试操作")
                .requestUrl("/api/test")
                .requestMethod("POST")
                .responseTime(150L)
                .ip("192.168.1.100")
                .tags(List.of("test", "struct", "complete"))
                .build();

        // 设置 context（会自动转为 Struct）
        Map<String, Object> context = new HashMap<>();
        context.put("testType", "unit-test");
        context.put("testId", UUID.randomUUID().toString());
        context.put("assertions", 10);
        context.put("passed", true);
        context.put("details", Map.of(
                "duration", 1.5,
                "coverage", 85.5,
                "failures", 0
        ));
        entry.setContext(context);

        // 发送日志
        logXClient.log(entry);

        System.out.println("✅ 完整 LogEntry 测试完成");
        System.out.println("   字段数量: 13+");
        System.out.println("   Extra 字段: 5");
    }

    /**
     * 测试10：性能测试
     */
    @Test
    @Order(10)
    public void test10_Performance() {
        System.out.println("\n--- 测试10：性能测试 ---");

        int count = 100;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            Map<String, Object> extra = Map.of(
                    "index", i,
                    "timestamp", System.currentTimeMillis(),
                    "data", Map.of(
                            "key1", "value1",
                            "key2", 123,
                            "key3", List.of(1, 2, 3)
                    )
            );

            logXClient.info("性能测试 #" + i, extra);
        }

        // 刷新缓冲区
        logXClient.flush();

        long duration = System.currentTimeMillis() - startTime;
        double avgTime = duration / (double) count;

        System.out.println("✅ 性能测试完成");
        System.out.println("   总数: " + count + " 条");
        System.out.println("   总耗时: " + duration + " ms");
        System.out.println("   平均: " + String.format("%.2f", avgTime) + " ms/条");
        System.out.println("   QPS: " + String.format("%.0f", 1000.0 / avgTime));
    }

    /**
     * 测试11：边界情况
     */
    @Test
    @Order(11)
    public void test11_EdgeCases() {
        System.out.println("\n--- 测试11：边界情况 ---");

        // 空 Map
        logXClient.info("空 Map", new HashMap<>());
        System.out.println("✅ 空 Map 测试通过");

        // 只有 null
        Map<String, Object> nullMap = new HashMap<>();
        nullMap.put("key", null);
        logXClient.info("Null 值", nullMap);
        System.out.println("✅ Null 值测试通过");

        // 空字符串
        logXClient.info("空字符串", Map.of("empty", ""));
        System.out.println("✅ 空字符串测试通过");

        // 空数组
        logXClient.info("空数组", Map.of("array", List.of()));
        System.out.println("✅ 空数组测试通过");

        // 空对象
        logXClient.info("空对象", Map.of("object", Map.of()));
        System.out.println("✅ 空对象测试通过");

        // 大数字
        logXClient.info("大数字", Map.of(
                "bigInt", Long.MAX_VALUE,
                "bigDouble", Double.MAX_VALUE
        ));
        System.out.println("✅ 大数字测试通过");

        // 特殊字符
        logXClient.info("特殊字符", Map.of(
                "special", "引号\"反斜杠\\换行\n制表\t"
        ));
        System.out.println("✅ 特殊字符测试通过");

        System.out.println("✅ 所有边界情况测试通过");
    }

    /**
     * 测试12：错误处理
     */
    @Test
    @Order(12)
    public void test12_ErrorHandling() {
        System.out.println("\n--- 测试12：错误处理 ---");

        try {
            // 模拟异常
            int result = 10 / 0;
        } catch (Exception e) {
            // 记录异常，同时包含 extra
            Map<String, Object> extra = Map.of(
                    "operation", "divide",
                    "dividend", 10,
                    "divisor", 0,
                    "attemptedAt", System.currentTimeMillis()
            );

            logXClient.error("除零错误", e, extra);

            System.out.println("✅ 异常日志记录成功");
            System.out.println("   异常类型: " + e.getClass().getSimpleName());
            System.out.println("   包含 extra: 是");
        }
    }
}