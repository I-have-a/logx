# LogX 完整代码示例与使用案例

## 📑 目录

- [快速开始示例](#快速开始示例)
- [Spring Boot 集成](#spring-boot-集成)
- [纯Java集成](#纯java集成)
- [高级用例](#高级用例)
- [实际场景](#实际场景)
- [测试示例](#测试示例)

---

## 快速开始示例

### 最简单的例子 (3行代码)

```java
LogXClient client = LogXClient.builder()
    .tenantId("company_a").systemId("my_app").apiKey("sk_xxx")
    .gatewayUrl("http://localhost:10240").build();

client.info("Hello LogX!");  // 发送日志

client.shutdown();  // 关闭
```

---

## Spring Boot 集成

### 1. 添加依赖

**pom.xml**:
```xml
<dependencies>
    <!-- LogX SDK -->
    <dependency>
        <groupId>com.domidodo</groupId>
        <artifactId>logx-sdk-spring-boot-starter</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### 2. 配置文件

**application.yml**:
```yaml
logx:
  enabled: true
  tenant-id: company_a
  system-id: erp_system
  system-name: "ERP管理系统"
  api-key: sk_test_key_001
  
  # 网关配置
  mode: http  # 或 grpc
  gateway:
    url: http://localhost:10240
  
  # 缓冲配置
  buffer:
    enabled: true
    size: 1000
    flush-interval: 5s
  
  # AOP自动拦截
  aspect:
    enabled: true
    controller: true
    service: false
    log-args: true
    log-result: true
    slow-threshold: 3000  # 3秒
  
  # 用户上下文
  user-context:
    enabled: true
    source: [header, session, principal]
    user-id-header: X-User-Id
    user-name-header: X-User-Name
```

### 3. 自动拦截 Controller

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    /**
     * AOP 会自动记录：
     * - 请求URL: /api/users
     * - 请求方法: POST
     * - 请求参数: user
     * - 响应时间: 123ms
     * - 用户信息: userId, userName (从请求头自动获取)
     * - IP地址: 192.168.1.100
     */
    @PostMapping
    public Result<User> createUser(@RequestBody User user) {
        return userService.create(user);
    }
    
    /**
     * 慢请求告警示例
     * 如果处理时间 > 3秒，会记录 WARN 日志，打上 "slow-request" 标签
     */
    @GetMapping("/slow")
    public Result<List<User>> slowQuery() {
        Thread.sleep(5000);  // 模拟慢查询
        return userService.findAll();
    }
}
```

**自动记录的日志**:
```json
{
  "level": "INFO",
  "message": "Controller 执行: UserController.createUser",
  "className": "com.example.controller.UserController",
  "methodName": "createUser",
  "userId": "user123",
  "userName": "张三",
  "requestUrl": "/api/users",
  "requestMethod": "POST",
  "requestParams": "[User:name=张三]",
  "responseTime": 123,
  "ip": "192.168.1.100",
  "context": {
    "type": "Controller",
    "duration": "123ms",
    "result": "Result:success=true"
  }
}
```

### 4. 手动记录日志

**方式一: 注入 LogXClient**

```java
@Service
public class OrderService {
    
    @Autowired
    private LogXClient logXClient;
    
    public void createOrder(Order order) {
        // 记录业务日志
        Map<String, Object> extra = new HashMap<>();
        extra.put("orderId", order.getId());
        extra.put("amount", order.getAmount());
        extra.put("items", order.getItems().size());
        
        logXClient.info("订单创建成功", extra);
        
        try {
            // 业务逻辑
            orderRepository.save(order);
        } catch (Exception e) {
            logXClient.error("订单创建失败", e, extra);
            throw e;
        }
    }
}
```

**方式二: 使用静态 Logger**

```java
@Service
public class PaymentService {
    
    private static final LogXLogger logger = LogXLogger.getLogger(PaymentService.class);
    
    public void processPayment(Payment payment) {
        logger.info("开始处理支付: " + payment.getId());
        
        try {
            // 支付逻辑
            paymentGateway.charge(payment);
            logger.info("支付成功");
        } catch (Exception e) {
            logger.error("支付失败", e);
            throw e;
        }
    }
}
```

### 5. 自定义用户上下文

**场景**: 从JWT Token获取用户信息

```java
@Component
public class JwtUserContextProvider implements UserContextProvider {
    
    @Autowired
    private TokenService tokenService;
    
    @Override
    public String getUserId(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            Claims claims = tokenService.parseToken(token);
            return claims.getSubject();
        }
        return null;
    }
    
    @Override
    public String getUserName(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            Claims claims = tokenService.parseToken(token);
            return claims.get("name", String.class);
        }
        return null;
    }
    
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

**配置使用自定义Provider**:
```yaml
logx:
  user-context:
    enabled: true
    custom-provider-bean-name: jwtUserContextProvider
```

---

## 纯Java集成

### 1. 添加依赖

**pom.xml**:
```xml
<dependency>
    <groupId>com.domidodo</groupId>
    <artifactId>logx-sdk-core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 2. 创建客户端

```java
public class Application {
    
    private static LogXClient client;
    
    public static void main(String[] args) {
        // 1. 初始化客户端
        client = LogXClient.builder()
            .tenantId("company_a")
            .systemId("standalone_app")
            .systemName("独立应用")
            .apiKey("sk_test_key_001")
            .gatewayUrl("http://localhost:10240")
            .bufferEnabled(true)
            .bufferSize(1000)
            .flushInterval(Duration.ofSeconds(5))
            .build();
        
        // 2. 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            client.flush();
            client.shutdown();
        }));
        
        // 3. 使用日志
        client.info("应用启动");
        
        // 业务逻辑
        runBusiness();
    }
    
    private static void runBusiness() {
        try {
            client.info("处理业务");
            
            // 模拟业务
            processOrder();
            
        } catch (Exception e) {
            client.error("业务异常", e);
        }
    }
    
    private static void processOrder() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("orderId", "ORDER-12345");
        extra.put("amount", 99.99);
        
        client.info("订单处理完成", extra);
    }
}
```

### 3. 使用gRPC

```java
LogXClient client = LogXClient.builder()
    .tenantId("company_a")
    .systemId("grpc_app")
    .apiKey("sk_test_key_001")
    .grpcEndpoint("localhost", 10241)  // gRPC模式
    .batchMode("stream")  // stream 或 batch
    .build();

client.info("使用gRPC发送日志");
```

---

## 高级用例

### 1. 完整的日志实体

```java
// 构建完整日志
LogEntry entry = LogEntry.builder()
    // 基础信息
    .level("INFO")
    .message("用户登录成功")
    .timestamp(LocalDateTime.now())
    
    // 追踪信息
    .traceId(UUID.randomUUID().toString())
    .spanId("span-001")
    
    // 用户信息
    .userId("user123")
    .userName("张三")
    
    // 业务信息
    .module("auth")
    .operation("login")
    
    // 请求信息
    .requestUrl("/api/auth/login")
    .requestMethod("POST")
    .requestParams("{\"username\":\"zhangsan\"}")
    .responseTime(123L)
    
    // 网络信息
    .ip("192.168.1.100")
    .userAgent("Mozilla/5.0...")
    
    .build();

// 添加标签
entry.addTag("important");
entry.addTag("audit");

// 添加扩展字段
entry.putContext("loginMethod", "password");
entry.putContext("deviceType", "mobile");
entry.putContext("location", "Beijing");

// 发送
client.log(entry);
```

### 2. Protobuf Struct 扩展字段

```java
// 复杂的扩展数据
Map<String, Object> extra = new HashMap<>();
extra.put("order", Map.of(
    "orderId", "ORDER-12345",
    "items", List.of(
        Map.of("name", "商品A", "price", 99.99, "qty", 2),
        Map.of("name", "商品B", "price", 49.99, "qty", 1)
    ),
    "totalAmount", 249.97,
    "discount", 10.0
));

LogEntry entry = LogEntry.builder()
    .level("INFO")
    .message("订单创建")
    .build();

// 设置 extra (自动转为 Protobuf Struct)
entry.setExtraMap(extra);

client.log(entry);
```

**发送到服务端的数据**:
```protobuf
message LogEntry {
    string level = 6;
    string message = 12;
    google.protobuf.Struct extra = 25;  // 包含整个嵌套结构
}
```

### 3. 异常处理

```java
try {
    // 业务逻辑
    orderService.create(order);
    
} catch (ValidationException e) {
    // 业务异常 - WARN级别
    Map<String, Object> extra = new HashMap<>();
    extra.put("orderId", order.getId());
    extra.put("validationErrors", e.getErrors());
    
    client.warn("订单验证失败", extra);
    
} catch (Exception e) {
    // 系统异常 - ERROR级别
    LogEntry entry = LogEntry.builder()
        .level("ERROR")
        .message("订单创建失败")
        .module("order")
        .operation("create")
        .build();
    
    // 自动格式化异常堆栈
    entry.setThrowable(e);
    
    client.log(entry);
    
    throw e;
}
```

---

## 实际场景

### 场景1: 电商订单系统

```java
@Service
public class OrderService {
    
    private static final LogXLogger logger = LogXLogger.getLogger(OrderService.class);
    
    @Autowired
    private LogXClient logXClient;
    
    /**
     * 创建订单 - 完整日志记录
     */
    public Order createOrder(OrderDTO dto, String userId) {
        String traceId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        
        // 1. 开始日志
        Map<String, Object> context = new HashMap<>();
        context.put("traceId", traceId);
        context.put("userId", userId);
        context.put("itemCount", dto.getItems().size());
        context.put("totalAmount", dto.getTotalAmount());
        
        logger.info("开始创建订单", context);
        
        try {
            // 2. 验证库存
            for (OrderItem item : dto.getItems()) {
                if (!inventoryService.checkStock(item.getProductId(), item.getQuantity())) {
                    context.put("productId", item.getProductId());
                    logger.warn("库存不足", context);
                    throw new StockException("库存不足");
                }
            }
            
            // 3. 创建订单
            Order order = new Order();
            order.setId(generateOrderId());
            order.setUserId(userId);
            order.setItems(dto.getItems());
            order.setTotalAmount(dto.getTotalAmount());
            orderRepository.save(order);
            
            // 4. 扣减库存
            for (OrderItem item : dto.getItems()) {
                inventoryService.deduct(item.getProductId(), item.getQuantity());
            }
            
            // 5. 成功日志
            long duration = System.currentTimeMillis() - startTime;
            context.put("orderId", order.getId());
            context.put("duration", duration + "ms");
            
            LogEntry entry = LogEntry.builder()
                .level("INFO")
                .message("订单创建成功")
                .traceId(traceId)
                .userId(userId)
                .module("order")
                .operation("create")
                .responseTime(duration)
                .build();
            
            entry.setExtraMap(context);
            entry.addTag("success");
            entry.addTag("order-created");
            
            logXClient.log(entry);
            
            return order;
            
        } catch (StockException e) {
            // 业务异常
            context.put("error", "库存不足");
            logger.warn("订单创建失败: 库存不足", context);
            throw e;
            
        } catch (Exception e) {
            // 系统异常
            LogEntry entry = LogEntry.builder()
                .level("ERROR")
                .message("订单创建失败: " + e.getMessage())
                .traceId(traceId)
                .userId(userId)
                .module("order")
                .operation("create")
                .build();
            
            entry.setThrowable(e);
            entry.setExtraMap(context);
            entry.addTag("error");
            entry.addTag("order-failed");
            
            logXClient.log(entry);
            
            throw e;
        }
    }
}
```

### 场景2: 定时任务日志

```java
@Component
public class DataSyncTask {
    
    private static final LogXLogger logger = LogXLogger.getLogger(DataSyncTask.class);
    
    @Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨2点
    public void syncData() {
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failCount = 0;
        
        Map<String, Object> context = new HashMap<>();
        context.put("taskName", "数据同步");
        context.put("startTime", LocalDateTime.now());
        
        logger.info("开始数据同步", context);
        
        try {
            List<Data> dataList = dataSource.fetchAll();
            
            for (Data data : dataList) {
                try {
                    target.save(data);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    logger.error("数据同步失败: " + data.getId(), e);
                }
            }
            
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            context.put("successCount", successCount);
            context.put("failCount", failCount);
            context.put("duration", duration + "ms");
            
            logger.info("数据同步完成", context);
        }
    }
}
```

### 场景3: 微服务调用链

```java
@Service
public class UserService {
    
    @Autowired
    private LogXClient logXClient;
    
    @Autowired
    private RestTemplate restTemplate;
    
    /**
     * 调用其他微服务，传递 traceId
     */
    public UserInfo getUserInfo(String userId) {
        // 生成 traceId
        String traceId = UUID.randomUUID().toString();
        
        // 记录开始
        LogEntry entry = LogEntry.builder()
            .level("INFO")
            .message("调用用户服务")
            .traceId(traceId)
            .spanId("span-user-service")
            .userId(userId)
            .module("user")
            .operation("getUserInfo")
            .build();
        
        logXClient.log(entry);
        
        // 调用远程服务，传递 traceId
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", traceId);
        headers.set("X-Span-Id", "span-user-service");
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<UserInfo> response = restTemplate.exchange(
            "http://user-service/api/users/" + userId,
            HttpMethod.GET,
            request,
            UserInfo.class
        );
        
        return response.getBody();
    }
}

// 被调用服务中
@RestController
public class UserController {
    
    @GetMapping("/api/users/{userId}")
    public UserInfo getUser(
            @PathVariable String userId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Span-Id", required = false) String spanId) {
        
        // 使用传递过来的 traceId
        LogEntry entry = LogEntry.builder()
            .level("INFO")
            .message("获取用户信息")
            .traceId(traceId)
            .spanId(spanId + "-handler")
            .userId(userId)
            .module("user")
            .operation("getUser")
            .build();
        
        logXClient.log(entry);
        
        return userService.findById(userId);
    }
}
```

---

## 测试示例

### 1. 单元测试

```java
@SpringBootTest
public class LogXIntegrationTest {
    
    @Autowired
    private LogXClient logXClient;
    
    @Test
    public void testLogSending() {
        // 发送测试日志
        Map<String, Object> extra = new HashMap<>();
        extra.put("testCase", "testLogSending");
        extra.put("timestamp", System.currentTimeMillis());
        
        logXClient.info("测试日志发送", extra);
        
        // 手动刷新
        logXClient.flush();
        
        // 等待发送完成
        Thread.sleep(1000);
    }
    
    @Test
    public void testExceptionLogging() {
        try {
            throw new RuntimeException("测试异常");
        } catch (Exception e) {
            logXClient.error("异常测试", e);
        }
        
        logXClient.flush();
    }
}
```

### 2. 性能测试

```java
public class PerformanceTest {
    
    public static void main(String[] args) throws Exception {
        LogXClient client = LogXClient.builder()
            .tenantId("test")
            .systemId("perf_test")
            .apiKey("sk_test")
            .gatewayUrl("http://localhost:10240")
            .bufferEnabled(true)
            .bufferSize(5000)
            .build();
        
        int totalLogs = 100000;
        long startTime = System.currentTimeMillis();
        
        // 发送10万条日志
        for (int i = 0; i < totalLogs; i++) {
            Map<String, Object> extra = new HashMap<>();
            extra.put("index", i);
            extra.put("timestamp", System.currentTimeMillis());
            
            client.info("性能测试日志 " + i, extra);
            
            if (i % 10000 == 0) {
                System.out.println("已发送: " + i);
            }
        }
        
        // 刷新并关闭
        client.flush();
        client.shutdown();
        
        long duration = System.currentTimeMillis() - startTime;
        double qps = (double) totalLogs / (duration / 1000.0);
        
        System.out.println("总日志数: " + totalLogs);
        System.out.println("总耗时: " + duration + "ms");
        System.out.println("QPS: " + String.format("%.2f", qps));
    }
}
```

**预期结果** (HTTP模式):
```
总日志数: 100000
总耗时: 15200ms
QPS: 6578.95
```

**预期结果** (gRPC模式):
```
总日志数: 100000
总耗时: 6800ms
QPS: 14705.88
```

---

## 常见模式总结

### 1. 日志级别使用

```java
// DEBUG - 详细调试
client.debug("查询参数: " + params);

// INFO - 正常业务
client.info("用户登录成功");

// WARN - 可恢复异常
client.warn("缓存失败，使用降级策略");

// ERROR - 需要关注
client.error("支付失败", exception);
```

### 2. 上下文传递

```java
// 方法1: 使用 ThreadLocal
public class TraceContext {
    private static ThreadLocal<String> traceId = new ThreadLocal<>();
    
    public static void setTraceId(String id) {
        traceId.set(id);
    }
    
    public static String getTraceId() {
        return traceId.get();
    }
}

// 方法2: 显式传递
public void processOrder(Order order, String traceId) {
    LogEntry entry = LogEntry.builder()
        .traceId(traceId)
        .message("处理订单")
        .build();
    client.log(entry);
}
```

### 3. 批量操作日志

```java
int successCount = 0;
int failCount = 0;

for (Item item : items) {
    try {
        process(item);
        successCount++;
    } catch (Exception e) {
        failCount++;
    }
}

// 汇总日志
Map<String, Object> summary = new HashMap<>();
summary.put("total", items.size());
summary.put("success", successCount);
summary.put("failed", failCount);

client.info("批量处理完成", summary);
```

---

## 最佳实践

1. ✅ **使用缓冲**: 开启缓冲提高性能
2. ✅ **添加上下文**: 使用 extra 字段记录业务数据
3. ✅ **传递 traceId**: 微服务间传递追踪ID
4. ✅ **合理级别**: 生产环境避免过多DEBUG日志
5. ✅ **异常记录**: 使用 setThrowable() 记录完整堆栈
6. ✅ **关闭客户端**: 应用退出前调用 shutdown()
7. ❌ **避免敏感信息**: 不要记录密码、密钥等
8. ❌ **避免过大对象**: extra 字段不要超过1MB

---

**下一步**: 查看 [Engine模块文档](./LogX-Engine-Guide.md) 了解日志处理流程
