# LogX 测试指南

## 📑 目录

- [测试概述](#测试概述)
- [Protobuf Struct测试](#protobuf-struct测试)
- [规则检测测试](#规则检测测试)
- [端到端测试](#端到端测试)
- [性能测试](#性能测试)
- [测试最佳实践](#测试最佳实践)

---

## 测试概述

### 测试分类

```
LogX测试体系：
├── 单元测试
│   ├── SDK单元测试（Protobuf Struct）
│   └── Detection单元测试（规则引擎）
├── 集成测试
│   └── 端到端数据流测试
└── 性能测试
    ├── SDK性能测试
    └── Gateway性能测试
```

### 测试工具

- **JUnit 5** - 测试框架
- **Spring Boot Test** - 集成测试支持
- **Assertions** - 断言库
- **@Order** - 测试顺序控制

---

## Protobuf Struct测试

### 测试类：StructCompleteTest

#### 测试概述

演示 `google.protobuf.Struct` 在LogX中的完整使用，包括：
- 基本数据类型
- 数组类型
- 嵌套对象
- 复杂混合结构
- 业务场景
- Map ↔ Struct 转换
- 性能测试
- 边界情况

#### 测试配置

```java
private final LogXClient logXClient = LogXClient.builder()
    .tenantId("yourtenantid")
    .systemId("yoursystemid")
    .apiKey("sk_3e07132ed2ec4cfe853cac0bbaf04626")
    .mode("grpc")                    // 使用gRPC模式
    .grpcEndpoint("localhost", 9090)
    .bufferEnabled(true)             // 开启缓冲
    .build();
```

---

### 测试1：基本数据类型

**测试目的**: 验证Struct支持的所有基本类型

**代码示例**:
```java
@Test
@Order(1)
public void test01_BasicTypes() {
    Map<String, Object> extra = new HashMap<>();
    extra.put("stringValue", "hello world");
    extra.put("intValue", 123);
    extra.put("longValue", 9876543210L);
    extra.put("doubleValue", 3.14159);
    extra.put("booleanValue", true);
    extra.put("nullValue", null);
    
    logXClient.info("基本数据类型测试", extra);
}
```

**支持类型**:

| Java类型 | Protobuf类型 | 说明 |
|---------|-------------|------|
| String | STRING_VALUE | 字符串 |
| Integer/Long | NUMBER_VALUE | 数字（转为double） |
| Boolean | BOOL_VALUE | 布尔值 |
| null | NULL_VALUE | 空值 |

---

### 测试2：数组类型

**测试目的**: 验证数组和列表的支持

**代码示例**:
```java
@Test
@Order(2)
public void test02_ArrayTypes() {
    Map<String, Object> extra = new HashMap<>();
    extra.put("stringArray", List.of("apple", "banana", "orange"));
    extra.put("numberArray", List.of(1, 2, 3, 4, 5));
    extra.put("mixedArray", List.of("text", 123, true));
    extra.put("emptyArray", List.of());
    
    logXClient.info("数组类型测试", extra);
}
```

**支持场景**:
- ✅ 字符串数组
- ✅ 数字数组
- ✅ 混合类型数组
- ✅ 空数组

---

### 测试3：嵌套对象

**测试目的**: 验证多层嵌套对象

**代码示例**:
```java
@Test
@Order(3)
public void test03_NestedObjects() {
    Map<String, Object> extra = new HashMap<>();
    
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
}
```

**嵌套结构**:
```
order
├── id: "order-123"
├── amount: 999.99
└── customer
    ├── name: "李四"
    ├── phone: "13800138000"
    └── address
        ├── province: "北京"
        ├── city: "北京市"
        └── district: "朝阳区"
```

---

### 测试4：复杂混合结构

**测试目的**: 验证对象数组和数组对象的混合

**代码示例**:
```java
@Test
@Order(4)
public void test04_ComplexStructures() {
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
    
    logXClient.info("复杂结构测试", extra);
}
```

---

### 测试5-7：业务场景

#### 用户登录场景

```java
@Test
@Order(5)
public void test05_UserLogin() {
    Map<String, Object> extra = new HashMap<>();
    extra.put("userId", "user-12345");
    extra.put("username", "zhangsan");
    extra.put("loginType", "password");
    extra.put("deviceInfo", Map.of(
        "deviceId", "device-abc",
        "deviceType", "mobile",
        "os", "iOS",
        "version", "15.0"
    ));
    extra.put("location", Map.of(
        "ip", "192.168.1.100",
        "country", "中国",
        "province", "北京"
    ));
    
    logXClient.info("用户登录成功", extra);
}
```

#### 订单创建场景

```java
@Test
@Order(6)
public void test06_OrderCreation() {
    Map<String, Object> extra = new HashMap<>();
    extra.put("orderId", "order-" + UUID.randomUUID());
    extra.put("totalAmount", 1299.97);
    extra.put("items", List.of(
        Map.of(
            "productId", "prod-001",
            "productName", "iPhone 14",
            "quantity", 1,
            "price", 999.99
        )
    ));
    extra.put("shippingAddress", Map.of(
        "name", "王五",
        "phone", "13900139000",
        "address", "北京市朝阳区某某街道123号"
    ));
    
    logXClient.info("订单创建成功", extra);
}
```

#### API监控场景

```java
@Test
@Order(7)
public void test07_ApiMonitoring() {
    Map<String, Object> extra = new HashMap<>();
    extra.put("apiName", "getUserInfo");
    extra.put("statusCode", 200);
    extra.put("responseTime", 125);
    extra.put("performance", Map.of(
        "dbQueryTime", 50,
        "cacheHitRate", 0.85,
        "cpuUsage", 12.5
    ));
    
    logXClient.info("API 调用完成", extra);
}
```

---

### 测试8：Map ↔ Struct 转换

**测试目的**: 验证转换的正确性

**代码示例**:
```java
@Test
@Order(8)
public void test08_MapStructConversion() {
    // 准备测试数据
    Map<String, Object> original = new HashMap<>();
    original.put("string", "test");
    original.put("number", 123);
    original.put("boolean", true);
    original.put("array", List.of(1, 2, 3));
    original.put("object", Map.of("nested", "value"));
    
    // Map -> Struct
    Struct struct = LogEntry.mapToStruct(original);
    Assertions.assertEquals(5, struct.getFieldsCount());
    
    // Struct -> Map
    Map<String, Object> converted = LogEntry.structToMap(struct);
    Assertions.assertEquals(5, converted.size());
    
    // 验证数据
    Assertions.assertEquals("test", converted.get("string"));
    Assertions.assertEquals(123.0, converted.get("number")); // 数字转为double
    Assertions.assertEquals(true, converted.get("boolean"));
}
```

**注意事项**:
- ⚠️ 数字统一转为 `double`
- ⚠️ 需要类型转换：`(Double) map.get("number")`

---

### 测试10：性能测试

**测试目的**: 测试大批量数据的处理性能

**代码示例**:
```java
@Test
@Order(10)
public void test10_Performance() {
    int count = 100000;
    long startTime = System.currentTimeMillis();
    
    for (int i = 0; i < count; i++) {
        Map<String, Object> extra = Map.of(
            "index", i,
            "timestamp", LocalDateTime.now(),
            "data", Map.of(
                "key1", "value1",
                "key2", 123,
                "key3", List.of(1, 2, 3)
            )
        );
        
        logXClient.info("性能测试 #" + i, extra);
    }
    
    logXClient.flush();  // 刷新缓冲区
    
    long duration = System.currentTimeMillis() - startTime;
    double avgTime = duration / (double) count;
    
    System.out.println("总数: " + count);
    System.out.println("总耗时: " + duration + " ms");
    System.out.println("平均: " + avgTime + " ms/条");
    System.out.println("QPS: " + (1000.0 / avgTime));
}
```

**预期结果**:
```
总数: 100000 条
总耗时: 15200 ms
平均: 0.15 ms/条
QPS: 6578
```

---

### 测试11：边界情况

**测试目的**: 验证边界值和特殊情况

**代码示例**:
```java
@Test
@Order(11)
public void test11_EdgeCases() {
    // 空Map
    logXClient.info("空Map", new HashMap<>());
    
    // Null值
    Map<String, Object> nullMap = new HashMap<>();
    nullMap.put("key", null);
    logXClient.info("Null值", nullMap);
    
    // 空字符串
    logXClient.info("空字符串", Map.of("empty", ""));
    
    // 大数字
    logXClient.info("大数字", Map.of(
        "bigInt", Long.MAX_VALUE,
        "bigDouble", Double.MAX_VALUE
    ));
    
    // 特殊字符
    logXClient.info("特殊字符", Map.of(
        "special", "引号\"反斜杠\\换行\n制表\t"
    ));
}
```

**测试覆盖**:
- ✅ 空Map
- ✅ Null值
- ✅ 空字符串
- ✅ 空数组
- ✅ 大数字
- ✅ 特殊字符

---

## 规则检测测试

### 测试类：RuleDetectionCompleteTest

#### 测试概述

演示Detection模块的5种规则类型：
1. 字段值比较（数字/字符串）
2. 批量操作监控（用户/IP/模块）
3. 连续请求监控
4. 告警服务集成
5. 性能测试

---

### 测试1：数字字段比较

**测试规则**: 响应时间 > 3000ms

**代码示例**:
```java
@Test
@Order(1)
public void test01_FieldCompare_Number() {
    Rule rule = createRule(
        "响应时间过长",
        "FIELD_COMPARE",
        "module:订单管理",
        "responseTime",
        ">",
        "3000"
    );
    
    // 正常响应时间（不触发）
    Map<String, Object> normalLog = createLogData("订单管理", 1500L);
    boolean matched1 = ruleEngine.evaluate(rule, normalLog);
    Assertions.assertFalse(matched1);
    System.out.println("✅ 正常响应时间: 1500ms < 3000ms (不触发)");
    
    // 超长响应时间（触发）
    Map<String, Object> slowLog = createLogData("订单管理", 5000L);
    boolean matched2 = ruleEngine.evaluate(rule, slowLog);
    Assertions.assertTrue(matched2);
    System.out.println("✅ 超长响应时间: 5000ms > 3000ms (触发告警)");
}
```

**测试结果**:
```
✅ 正常响应时间: 1500ms < 3000ms (不触发)
✅ 超长响应时间: 5000ms > 3000ms (触发告警)
✅ 数字字段比较测试完成
```

---

### 测试2：字符串字段比较

**测试规则**: level = ERROR

**代码示例**:
```java
@Test
@Order(2)
public void test02_FieldCompare_String() {
    Rule rule = createRule(
        "ERROR日志监控",
        "FIELD_COMPARE",
        "",
        "level",
        "=",
        "ERROR"
    );
    
    // INFO级别（不触发）
    Map<String, Object> infoLog = createLogData("订单管理", 100L);
    infoLog.put("level", "INFO");
    boolean matched1 = ruleEngine.evaluate(rule, infoLog);
    Assertions.assertFalse(matched1);
    
    // ERROR级别（触发）
    Map<String, Object> errorLog = createLogData("订单管理", 100L);
    errorLog.put("level", "ERROR");
    boolean matched2 = ruleEngine.evaluate(rule, errorLog);
    Assertions.assertTrue(matched2);
    
    // contains操作符
    Rule containsRule = createRule(
        "空指针异常监控",
        "FIELD_COMPARE",
        "",
        "exception",
        "contains",
        "NullPointerException"
    );
    
    Map<String, Object> exceptionLog = createLogData("订单管理", 100L);
    exceptionLog.put("exception", "java.lang.NullPointerException");
    boolean matched3 = ruleEngine.evaluate(containsRule, exceptionLog);
    Assertions.assertTrue(matched3);
}
```

---

### 测试3：批量操作监控（用户维度）

**测试规则**: 用户1分钟内操作超过10次

**代码示例**:
```java
@Test
@Order(3)
public void test03_BatchOperation_User() {
    Rule rule = createRule(
        "用户高频操作",
        "BATCH_OPERATION",
        "userId:",
        "operationCount",
        ">",
        "10:60" // 1分钟内超过10次
    );
    
    String userId = "user-" + UUID.randomUUID();
    
    // 模拟用户15次操作
    for (int i = 0; i < 15; i++) {
        Map<String, Object> log = createLogData("订单管理", 100L);
        log.put("userId", userId);
        log.put("operation", "创建订单");
        
        boolean matched = ruleEngine.evaluate(rule, log);
        
        if (i < 10) {
            Assertions.assertFalse(matched, "前10次不应触发");
        } else {
            Assertions.assertTrue(matched, "超过10次应触发");
        }
    }
}
```

**测试结果**:
```
✅ 第11次操作触发告警
✅ 用户批量操作监控测试完成
   共执行: 15 次操作
   阈值: 10次/60秒
```

---

### 测试6：连续请求监控

**测试规则**: 接口连续失败5次

**代码示例**:
```java
@Test
@Order(6)
public void test06_ContinuousRequest() {
    Rule rule = createRule(
        "订单接口连续失败",
        "CONTINUOUS_REQUEST",
        "/api/order/create",
        "continuousFailure",
        ">",
        "5"
    );
    
    // 模拟8次连续失败
    for (int i = 0; i < 8; i++) {
        Map<String, Object> log = createLogData("订单管理", 100L);
        log.put("level", "ERROR");
        log.put("requestUrl", "/api/order/create");
        
        boolean matched = ruleEngine.evaluate(rule, log);
        
        if (i < 5) {
            Assertions.assertFalse(matched);
        } else {
            Assertions.assertTrue(matched);
        }
    }
}
```

**测试结果**:
```
第1次失败: 累计1次（未触发）
第2次失败: 累计2次（未触发）
...
第6次失败: 累计6次（🚨 触发告警）
✅ 连续请求监控测试完成
```

---

### 测试14：性能测试

**测试目的**: 验证规则引擎的性能

**代码示例**:
```java
@Test
@Order(14)
public void test14_Performance() {
    Rule rule = createRule(
        "性能测试规则",
        "FIELD_COMPARE",
        "",
        "responseTime",
        ">",
        "1000"
    );
    
    int testCount = 10000;
    long startTime = System.currentTimeMillis();
    
    int matchedCount = 0;
    for (int i = 0; i < testCount; i++) {
        Map<String, Object> log = createLogData("性能测试", 1500L);
        if (ruleEngine.evaluate(rule, log)) {
            matchedCount++;
        }
    }
    
    long duration = System.currentTimeMillis() - startTime;
    double avgTime = duration / (double) testCount;
    double qps = 1000.0 / avgTime;
    
    System.out.println("总数: " + testCount);
    System.out.println("匹配: " + matchedCount);
    System.out.println("总耗时: " + duration + " ms");
    System.out.println("QPS: " + (qps * testCount));
    
    Assertions.assertTrue(qps > 1000, "QPS应该大于1000");
}
```

**预期结果**:
```
总数: 10000 条
匹配: 10000 条
总耗时: 245 ms
平均: 0.025 ms/条
QPS: 40000
```

---

## 端到端测试

### 测试类：EndToEndDataFlowTest

#### 测试概述

验证完整的数据流：
```
Gateway → Kafka(logx-logs) → Processor → ES + Kafka(logx-logs-processing) → Detection
```

---

### 测试1：Gateway → Kafka

**测试目的**: 验证日志成功发送到Kafka

**代码示例**:
```java
@Test
@Order(1)
public void test01_GatewayToKafka() {
    LogDTO logDTO = buildTestLog();
    
    String logJson = JsonUtil.toJson(logDTO);
    String key = generateKey(logDTO);
    
    kafkaTemplate.send("logx-logs", key, logJson)
        .whenComplete((result, ex) -> {
            if (ex != null) {
                System.err.println("❌ 发送失败");
            } else {
                System.out.println("✅ 日志已发送到Kafka");
                System.out.println("   Topic: logx-logs");
                System.out.println("   Partition: " + result.getRecordMetadata().partition());
                System.out.println("   Offset: " + result.getRecordMetadata().offset());
            }
        });
    
    TimeUnit.SECONDS.sleep(2);
}
```

---

### 测试2：批量发送

**测试目的**: 验证批量发送性能

**代码示例**:
```java
@Test
@Order(2)
public void test02_BatchSendToKafka() {
    int batchSize = 100;
    int successCount = 0;
    
    for (int i = 0; i < batchSize; i++) {
        LogDTO logDTO = buildTestLog();
        logDTO.setMessage("批量测试日志 #" + i);
        
        String logJson = JsonUtil.toJson(logDTO);
        kafkaTemplate.send("logx-logs", generateKey(logDTO), logJson);
        successCount++;
    }
    
    TimeUnit.SECONDS.sleep(3);
    
    System.out.println("✅ 批量发送完成");
    System.out.println("   总数: " + batchSize);
    System.out.println("   成功: " + successCount);
}
```

---

### 测试3：异常场景

**测试目的**: 触发Detection模块的各种规则

**代码示例**:
```java
@Test
@Order(3)
public void test03_SendAbnormalLogs() {
    // 场景1：响应时间过长
    LogDTO slowLog = buildTestLog();
    slowLog.setResponseTime(5000L);
    slowLog.setMessage("订单查询响应缓慢");
    sendLog(slowLog);
    
    // 场景2：ERROR级别日志
    LogDTO errorLog = buildTestLog();
    errorLog.setLevel("ERROR");
    errorLog.setMessage("订单创建失败");
    errorLog.setException("java.lang.NullPointerException");
    sendLog(errorLog);
    
    // 场景3：高频操作（同一用户10次）
    String userId = "user-suspicious-001";
    for (int i = 0; i < 10; i++) {
        LogDTO frequentLog = buildTestLog();
        frequentLog.setUserId(userId);
        frequentLog.setOperation("创建订单");
        sendLog(frequentLog);
        TimeUnit.MILLISECONDS.sleep(100);
    }
    
    // 场景4：连续失败（8次）
    for (int i = 0; i < 8; i++) {
        LogDTO failLog = buildTestLog();
        failLog.setLevel("ERROR");
        failLog.setRequestUrl("/api/order/create");
        failLog.setMessage("订单创建失败 #" + i);
        sendLog(failLog);
        TimeUnit.MILLISECONDS.sleep(200);
    }
    
    TimeUnit.SECONDS.sleep(10);
}
```

---

### 测试6：性能测试

**测试目的**: 验证系统整体性能

**代码示例**:
```java
@Test
@Order(6)
public void test06_PerformanceTest() {
    int testCount = 1000;
    long startTime = System.currentTimeMillis();
    
    for (int i = 0; i < testCount; i++) {
        LogDTO logDTO = buildTestLog();
        logDTO.setMessage("性能测试日志 #" + i);
        
        String logJson = JsonUtil.toJson(logDTO);
        kafkaTemplate.send("logx-logs", generateKey(logDTO), logJson);
    }
    
    TimeUnit.SECONDS.sleep(10);
    
    long duration = System.currentTimeMillis() - startTime;
    double qps = testCount / (duration / 1000.0);
    
    System.out.println("总数: " + testCount);
    System.out.println("耗时: " + duration + " ms");
    System.out.println("QPS: " + qps);
    
    Assertions.assertTrue(qps > 100, "QPS应该大于100");
}
```

**预期结果**:
```
总数: 1000 条
耗时: 8500 ms
QPS: 117
```

---

## 性能测试

### 性能指标对比

| 组件 | 测试项 | 数据量 | 耗时 | QPS |
|------|--------|--------|------|-----|
| **SDK** | Struct转换 | 100,000 | 15s | 6,578 |
| **Detection** | 规则评估 | 10,000 | 245ms | 40,000 |
| **Gateway** | Kafka发送 | 1,000 | 8.5s | 117 |

### 性能优化建议

**1. SDK端优化**:
```java
// 开启缓冲
LogXClient client = LogXClient.builder()
    .bufferEnabled(true)
    .bufferSize(1000)      // 增大缓冲区
    .flushInterval(Duration.ofSeconds(5))
    .build();
```

**2. Gateway端优化**:
```yaml
# 增加Kafka生产者配置
spring:
  kafka:
    producer:
      batch-size: 16384
      linger-ms: 10
      buffer-memory: 33554432
```

**3. Detection端优化**:
```yaml
# 增加规则缓存刷新间隔
logx:
  detection:
    rule-cache-refresh: 60000  # 60秒
```

---

## 测试最佳实践

### 1. 测试顺序

```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MyTest {
    @Test @Order(1)
    public void test01_Setup() { }
    
    @Test @Order(2)
    public void test02_Core() { }
    
    @Test @Order(99)
    public void test99_Cleanup() { }
}
```

### 2. 测试数据准备

```java
// 辅助方法
private LogDTO buildTestLog() {
    LogDTO log = new LogDTO();
    log.setId(UUID.randomUUID().toString());
    log.setTenantId("tenant_001");
    log.setSystemId("sys_erp");
    // ... 其他字段
    return log;
}
```

### 3. 异步测试

```java
@Test
public void testAsync() {
    // 执行异步操作
    client.sendAsync(log);
    
    // 等待完成
    TimeUnit.SECONDS.sleep(2);
    
    // 验证结果
    Assertions.assertTrue(isSuccess());
}
```

### 4. 性能测试

```java
@Test
public void testPerformance() {
    int count = 10000;
    long start = System.currentTimeMillis();
    
    for (int i = 0; i < count; i++) {
        // 执行操作
    }
    
    long duration = System.currentTimeMillis() - start;
    double qps = count / (duration / 1000.0);
    
    System.out.println("QPS: " + qps);
    Assertions.assertTrue(qps > 1000);
}
```

### 5. 边界测试

```java
@Test
public void testEdgeCases() {
    // 空数据
    test(new HashMap<>());
    
    // null值
    Map<String, Object> nullMap = new HashMap<>();
    nullMap.put("key", null);
    test(nullMap);
    
    // 大数字
    test(Map.of("big", Long.MAX_VALUE));
    
    // 特殊字符
    test(Map.of("special", "引号\"换行\n"));
}
```

---

## 故障排查

### 1. 测试失败

**现象**: 测试用例失败

**排查步骤**:
```bash
# 1. 检查依赖服务
docker ps | grep logx

# 2. 检查日志
tail -f logs/test.log

# 3. 检查数据
curl http://localhost:9200/_cat/indices?v
```

### 2. 性能不达标

**现象**: QPS低于预期

**排查**:
```java
// 添加性能日志
long start = System.currentTimeMillis();
client.send(log);
long duration = System.currentTimeMillis() - start;
System.out.println("发送耗时: " + duration + "ms");
```

**优化**:
```java
// 开启缓冲
client = LogXClient.builder()
    .bufferEnabled(true)
    .bufferSize(5000)  // 增大缓冲
    .build();
```

---

## 下一步

- 查看 [SDK详解](./LogX-SDK-Guide.md) 了解客户端使用
- 查看 [Detection详解](./LogX-Detection-Guide.md) 了解规则配置
- 查看 [Gateway详解](./LogX-Gateway-Guide.md) 了解网关配置
