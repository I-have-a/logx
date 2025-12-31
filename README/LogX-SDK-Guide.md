# LogX SDK 模块技术文档

## 📑 目录

- [SDK 架构](#sdk-架构)
- [核心组件](#核心组件)
- [集成指南](#集成指南)
- [高级特性](#高级特性)
- [最佳实践](#最佳实践)
- [性能优化](#性能优化)

---

## SDK 架构

### 模块组成

```
logx-sdk/
├── logx-sdk-core/              # 核心SDK（纯Java，最小依赖）
│   ├── LogXClient.java         # 主客户端，支持 HTTP/gRPC
│   ├── LogXLogger.java         # 静态日志记录器
│   ├── config/
│   │   └── LogXConfig.java     # 配置类
│   ├── model/
│   │   └── LogEntry.java       # 日志实体（支持 Protobuf Struct）
│   ├── buffer/
│   │   └── LogBuffer.java      # 日志缓冲区
│   └── sender/
│       ├── LogSender.java          # 发送器接口
│       ├── HttpLogSender.java      # HTTP 实现
│       └── GrpcLogSender.java      # gRPC 实现
│
└── logx-sdk-spring-boot-starter/  # Spring Boot 自动配置
    ├── LogXAutoConfiguration.java      # 自动配置类
    ├── LogXProperties.java             # 配置属性
    ├── aspect/
    │   └── LogAspect.java              # AOP 切面（自动拦截）
    └── context/
        ├── UserContextProvider.java           # 用户上下文接口
        └── DefaultUserContextProvider.java    # 默认实现
```

### 数据流

```
应用代码
    ↓
LogXLogger / LogXClient
    ↓
LogBuffer (可选缓冲)
    ↓
LogSender (HTTP/gRPC)
    ↓
Gateway (网关)
```

---

## 核心组件

### 1. LogEntry - 日志实体

**特性**:
- 支持 **google.protobuf.Struct** 类型的扩展字段
- 自动 Map ↔ Struct 转换
- 支持所有 gRPC Proto 定义的字段

**字段说明**:

| 类别 | 字段 | 类型 | 说明 |
|------|------|------|------|
| **追踪** | traceId | String | 分布式追踪ID |
| | spanId | String | 调用链ID |
| **租户** | tenantId | String | 租户ID |
| | systemId | String | 系统ID |
| **时间** | timestamp | LocalDateTime | 日志时间戳 |
| **日志** | level | String | DEBUG/INFO/WARN/ERROR |
| | message | String | 日志消息 |
| | exception | String | 异常堆栈 |
| **代码** | className | String | 类名 |
| | methodName | String | 方法名 |
| | lineNumber | Integer | 行号 |
| **用户** | userId | String | 用户ID |
| | userName | String | 用户名 |
| **业务** | module | String | 功能模块 |
| | operation | String | 操作类型 |
| **请求** | requestUrl | String | 请求URL |
| | requestMethod | String | GET/POST/PUT/DELETE |
| | responseTime | Long | 响应时间(ms) |
| **网络** | ip | String | 客户端IP |
| | userAgent | String | User-Agent |
| **扩展** | tags | List<String> | 标签列表 |
| | extra | Struct | 扩展字段（JSON） |
| | context | Map | 上下文信息 |

**代码示例**:

```java
// 构建日志实体
LogEntry entry = LogEntry.builder()
    .level("INFO")
    .message("用户登录")
    .userId("user123")
    .userName("张三")
    .module("auth")
    .operation("login")
    .build();

// 添加扩展字段（会转为 Struct）
entry.putContext("loginMethod", "password");
entry.putContext("deviceType", "mobile");

// 添加标签
entry.addTag("important");

// 设置异常
try {
    // ...
} catch (Exception e) {
    entry.setThrowable(e);  // 自动格式化堆栈
}
```

---

### 2. LogBuffer - 缓冲管理

**作用**: 批量发送，减少网络开销

**配置**:
```yaml
logx:
  buffer:
    enabled: true           # 是否启用缓冲
    size: 1000             # 缓冲区大小
    flush-interval: 5s     # 自动刷新间隔
```

**工作原理**:
```
日志 → 缓冲区 → 达到阈值或超时 → 批量发送
```

**关键代码**:
```java
public class LogBuffer {
    private final BlockingQueue<LogEntry> queue;
    
    public void add(LogEntry entry) {
        queue.offer(entry);  // 满了会丢弃最旧的
    }
    
    public List<LogEntry> drain() {
        List<LogEntry> entries = new ArrayList<>();
        queue.drainTo(entries);  // 一次取出所有
        return entries;
    }
}
```

---

### 3. LogSender - 发送器

#### HTTP 发送器

**特点**:
- 简单易用，兼容性好
- 支持重试机制
- JSON 序列化

**关键代码**:
```java
public class HttpLogSender implements LogSender {
    
    private void doSend(List<LogEntry> entries) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Tenant-Id", config.getTenantId());
        conn.setRequestProperty("X-API-Key", config.getApiKey());
        
        // JSON 序列化（包括 Struct → Map → JSON）
        String json = toJson(entries);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
```

#### gRPC 发送器

**特点**:
- 高性能，低延迟
- 支持流式传输
- Protobuf 序列化

**关键代码**:
```java
public class GrpcLogSender implements LogSender {
    
    // 批量发送
    public void sendBatchHttp(List<LogEntry> entries) {
        LogBatchRequest.Builder requestBuilder = LogBatchRequest.newBuilder()
            .setTenantId(config.getTenantId())
            .setSystemId(config.getSystemId());
        
        for (LogEntry entry : entries) {
            requestBuilder.addLogs(buildLogEntry(entry));
        }
        
        LogBatchResponse response = blockingStub.sendLogs(requestBuilder.build());
    }
    
    // 流式发送
    public void sendBatchStream(List<LogEntry> entries) {
        StreamObserver<LogBatchResponse> responseObserver = ...;
        StreamObserver<LogEntry> requestObserver = asyncStub.streamLogs(responseObserver);
        
        for (LogEntry entry : entries) {
            requestObserver.onNext(buildLogEntry(entry));
        }
        requestObserver.onCompleted();
    }
}
```

**性能对比**:

| 指标 | HTTP | gRPC |
|------|------|------|
| 吞吐量 | 6,578 logs/s | 14,705 logs/s (2.2x) |
| 网络流量 | 48MB | 32MB (-33%) |
| 延迟 | 15.2s (10万条) | 6.8s (10万条) |

---

### 4. LogXClient - 主客户端

**生命周期**:
```
创建 → 使用 → 刷新缓冲区 → 关闭
```

**完整示例**:
```java
public class Application {
    public static void main(String[] args) {
        // 1. 创建客户端
        LogXClient client = LogXClient.builder()
            .tenantId("company_a")
            .systemId("erp_system")
            .systemName("ERP系统")
            .apiKey("sk_test_key_001")
            .gatewayUrl("http://localhost:10240")  // HTTP 模式
            // .grpcEndpoint("localhost", 10241)   // 或 gRPC 模式
            .bufferEnabled(true)
            .bufferSize(1000)
            .build();
        
        // 2. 使用日志
        client.info("应用启动");
        
        // 带扩展字段
        Map<String, Object> extra = new HashMap<>();
        extra.put("version", "1.0.0");
        client.info("配置加载完成", extra);
        
        // 记录异常
        try {
            // 业务逻辑
        } catch (Exception e) {
            client.error("处理失败", e);
        }
        
        // 3. 应用退出时
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            client.flush();    // 刷新剩余日志
            client.shutdown(); // 关闭连接
        }));
    }
}
```

---

### 5. LogXLogger - 静态日志记录器

**用途**: 类似 SLF4J，适合替换现有日志框架

**使用示例**:
```java
public class UserService {
    private static final LogXLogger logger = LogXLogger.getLogger(UserService.class);
    
    public void createUser(User user) {
        logger.info("创建用户: " + user.getName());
        
        try {
            userRepository.save(user);
            logger.info("用户创建成功");
        } catch (Exception e) {
            logger.error("创建失败", e);
        }
    }
}
```

**自动字段**:
- className: 自动填充类名
- methodName: 自动填充方法名
- lineNumber: 自动填充行号
- thread: 自动填充线程名

---

## 集成指南

### Spring Boot 集成

#### 1. 添加依赖

```xml
<dependency>
    <groupId>com.domidodo</groupId>
    <artifactId>logx-sdk-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

#### 2. 配置属性

**最小配置**:
```yaml
logx:
  tenant-id: company_a
  system-id: erp_system
  system-name: "ERP管理系统"
  api-key: sk_test_key_001
  gateway:
    url: http://localhost:10240
```

**完整配置**:
```yaml
logx:
  enabled: true
  tenant-id: company_a
  system-id: erp_system
  system-name: "ERP管理系统"
  api-key: sk_test_key_001
  
  # 通信模式
  mode: http  # http 或 grpc
  
  # HTTP 网关
  gateway:
    url: http://localhost:10240
    connect-timeout: 5000
    read-timeout: 5000
  
  # gRPC 网关（如果 mode=grpc）
  # gateway:
  #   host: localhost
  #   port: 10241
  #   batch-mode: stream  # stream 或 batch
  
  # 缓冲配置
  buffer:
    enabled: true
    size: 1000
    flush-interval: 5s
  
  # AOP 切面
  aspect:
    enabled: true
    controller: true      # 拦截 Controller
    service: false        # 拦截 Service
    log-args: true        # 记录参数
    log-result: true      # 记录结果
    slow-threshold: 5000  # 慢请求阈值(ms)
  
  # 用户上下文
  user-context:
    enabled: true
    source: [header, session, principal]  # 获取顺序
    user-id-header: X-User-Id
    user-name-header: X-User-Name
    tenant-id-header: X-Tenant-Id
```

#### 3. 自动配置原理

```java
@Configuration
@EnableConfigurationProperties(LogXProperties.class)
public class LogXAutoConfiguration {
    
    @Bean
    public LogXClient logXClient(LogXProperties properties) {
        // 1. 验证配置
        validateConfig(properties);
        
        // 2. 创建客户端
        LogXClient client = LogXClient.builder()
            .tenantId(properties.getTenantId())
            .systemId(properties.getSystemId())
            // ... 其他配置
            .build();
        
        // 3. 初始化静态Logger
        LogXLogger.initClient(client);
        
        return client;
    }
    
    @Bean
    public UserContextProvider userContextProvider(LogXProperties properties) {
        // 支持自定义实现
        return new DefaultUserContextProvider(properties.getUserContext());
    }
    
    @Bean
    public LogAspect logAspect(LogXClient client, UserContextProvider provider) {
        return new LogAspect(client, properties, provider);
    }
}
```

---

### AOP 自动日志收集

#### 原理

```java
@Aspect
public class LogAspect {
    
    @Around("controllerPointcut()")
    public Object aroundController(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 提取方法信息
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
        
        // 2. 提取用户信息
        HttpServletRequest request = getRequest();
        String userId = userContextProvider.getUserId(request);
        String userName = userContextProvider.getUserName(request);
        
        // 3. 记录请求
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            
            // 4. 记录成功日志
            long duration = System.currentTimeMillis() - startTime;
            
            if (duration > slowThreshold) {
                // 慢请求告警
                logEntry.setLevel("WARN");
                logEntry.addTag("slow-request");
            }
            
            logXClient.log(logEntry);
            
            return result;
            
        } catch (Throwable e) {
            // 5. 记录异常日志
            logEntry.setLevel("ERROR");
            logEntry.setThrowable(e);
            logXClient.log(logEntry);
            
            throw e;
        }
    }
}
```

#### 效果

**拦截 Controller**:
```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @PostMapping
    public Result<User> create(@RequestBody User user) {
        // 自动记录：
        // - 请求URL: /api/users
        // - 请求方法: POST
        // - 请求参数: user
        // - 响应时间: 123ms
        // - 用户信息: userId, userName
        // - IP地址: 192.168.1.100
        
        return userService.create(user);
    }
}
```

**慢请求告警**:
```yaml
logx:
  aspect:
    slow-threshold: 3000  # 超过3秒的请求会记录 WARN 日志
```

---

### 用户上下文自动获取

#### 默认实现

```java
public class DefaultUserContextProvider implements UserContextProvider {
    
    @Override
    public String getUserId(HttpServletRequest request) {
        // 1. 从请求头获取
        String userId = request.getHeader("X-User-Id");
        if (userId != null) return userId;
        
        // 2. 从 Session 获取
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object userIdObj = session.getAttribute("userId");
            if (userIdObj != null) return userIdObj.toString();
        }
        
        // 3. 从 Principal 获取
        Principal principal = request.getUserPrincipal();
        if (principal != null) return principal.getName();
        
        return null;
    }
}
```

#### 自定义实现

```java
@Component
public class MyUserContextProvider implements UserContextProvider {
    
    @Autowired
    private TokenService tokenService;
    
    @Override
    public String getUserId(HttpServletRequest request) {
        // 从 JWT Token 解析用户ID
        String token = request.getHeader("Authorization");
        if (token != null) {
            Claims claims = tokenService.parseToken(token);
            return claims.getSubject();
        }
        return null;
    }
    
    @Override
    public String getUserName(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null) {
            Claims claims = tokenService.parseToken(token);
            return claims.get("name", String.class);
        }
        return null;
    }
}
```

**配置使用自定义实现**:
```yaml
logx:
  user-context:
    enabled: true
    custom-provider-bean-name: myUserContextProvider
```

---

## 高级特性

### 1. Protobuf Struct 支持

**作用**: 支持任意 JSON 结构的扩展字段

**Map ↔ Struct 转换**:
```java
// Map → Struct
Map<String, Object> map = new HashMap<>();
map.put("orderId", "12345");
map.put("amount", 99.99);
map.put("items", List.of("item1", "item2"));

Struct struct = LogEntry.mapToStruct(map);

// Struct → Map
Map<String, Object> map = LogEntry.structToMap(struct);
```

**自动合并 context 和 extra**:
```java
LogEntry entry = LogEntry.builder().build();

// 添加 context（简化接口）
entry.putContext("key1", "value1");

// 设置 extra Struct
Map<String, Object> extraMap = Map.of("key2", "value2");
entry.setExtraMap(extraMap);

// 发送时自动合并为一个 Struct
```

---

### 2. 双协议支持

#### HTTP vs gRPC 选择

| 场景 | 推荐 | 原因 |
|------|------|------|
| 开发环境 | HTTP | 简单易调试 |
| 低吞吐量 | HTTP | 足够使用 |
| 高吞吐量 | gRPC | 性能更好 |
| 有防火墙 | HTTP | 端口友好 |
| 内网通信 | gRPC | 效率最高 |

#### 切换方式

**HTTP 模式**:
```yaml
logx:
  mode: http
  gateway:
    url: http://localhost:10240
```

**gRPC 模式**:
```yaml
logx:
  mode: grpc
  gateway:
    host: localhost
    port: 10241
    batch-mode: stream  # stream 或 batch
```

#### gRPC 流式 vs 批量

```java
// Batch 模式：一次发送所有日志
public void sendBatchHttp(List<LogEntry> entries) {
    LogBatchRequest request = buildRequest(entries);
    LogBatchResponse response = blockingStub.sendLogs(request);
}

// Stream 模式：逐条发送（适合大批量）
public void sendBatchStream(List<LogEntry> entries) {
    StreamObserver<LogBatchResponse> responseObserver = ...;
    StreamObserver<LogEntry> requestObserver = asyncStub.streamLogs(responseObserver);
    
    for (LogEntry entry : entries) {
        requestObserver.onNext(buildLogEntry(entry));
    }
    requestObserver.onCompleted();
}
```

---

### 3. 异常自动捕获

```java
LogEntry entry = LogEntry.builder()
    .level("ERROR")
    .message("操作失败")
    .build();

try {
    // 业务逻辑
} catch (Exception e) {
    // 自动格式化堆栈
    entry.setThrowable(e);
    
    // 生成的 exception 字段：
    // java.lang.NullPointerException: Cannot read field
    //     at com.example.UserService.create(UserService.java:45)
    //     at com.example.UserController.create(UserController.java:23)
    // Caused by: ...
}
```

---

## 最佳实践

### 1. 日志级别使用

| 级别 | 使用场景 | 示例 |
|------|---------|------|
| DEBUG | 详细调试信息 | 变量值、SQL语句 |
| INFO | 重要业务流程 | 用户登录、订单创建 |
| WARN | 可恢复的异常 | 重试成功、降级处理 |
| ERROR | 需要关注的错误 | 业务失败、数据异常 |
| FATAL | 严重系统错误 | 数据库连接失败 |

### 2. 扩展字段规范

```java
// ✅ 好的做法
Map<String, Object> extra = new HashMap<>();
extra.put("orderId", order.getId());
extra.put("orderAmount", order.getAmount());
extra.put("paymentMethod", order.getPaymentMethod());
client.info("订单创建成功", extra);

// ❌ 避免
client.info("订单创建成功: " + order);  // 信息冗余
```

### 3. 敏感信息处理

```java
// ✅ 脱敏后记录
String phone = maskPhone(user.getPhone());  // 138****5678
extra.put("phone", phone);

// ❌ 不要记录原始敏感信息
extra.put("password", user.getPassword());  // 严禁
extra.put("idCard", user.getIdCard());      // 严禁
```

### 4. 性能考虑

```java
// ✅ 启用缓冲
logx:
  buffer:
    enabled: true
    size: 1000
    flush-interval: 5s

// ✅ 合理的日志量
// 正常请求: INFO
// 错误请求: ERROR
// 调试信息: DEBUG (生产环境关闭)

// ❌ 避免
for (int i = 0; i < 1000000; i++) {
    client.debug("循环: " + i);  // 会产生百万条日志
}
```

---

## 性能优化

### 1. 缓冲配置

```yaml
# 低流量（< 100 logs/s）
logx:
  buffer:
    size: 500
    flush-interval: 10s

# 中流量（100-1000 logs/s）
logx:
  buffer:
    size: 1000
    flush-interval: 5s

# 高流量（> 1000 logs/s）
logx:
  buffer:
    size: 5000
    flush-interval: 2s
```

### 2. 网络优化

```yaml
# HTTP 模式
logx:
  gateway:
    connect-timeout: 3000  # 减少连接超时
    read-timeout: 5000

# gRPC 模式（高性能）
logx:
  mode: grpc
  gateway:
    host: localhost
    port: 10241
```

### 3. 批量大小

```java
// SDK 默认配置
logx.buffer.size = 1000

// 网关批量消费
spring.kafka.consumer.max-poll-records = 500

// ES 批量写入
logx.es.bulk.max-size = 500
```

### 4. 异步处理

```yaml
logx:
  async:
    enabled: true
    core-pool-size: 2
    max-pool-size: 5
    queue-capacity: 500
```

---

## 故障排查

### 常见问题

#### 1. 日志未发送

**检查清单**:
```bash
# 1. 检查配置
# 确认 api-key 正确
# 确认 gateway.url 可访问

# 2. 检查缓冲区
client.flush();  # 手动刷新

# 3. 查看日志
# SDK 内部日志会输出发送失败原因
```

#### 2. 性能问题

```java
// 问题：发送太慢
// 解决：启用缓冲 + gRPC

logx:
  buffer:
    enabled: true
    size: 1000
  mode: grpc
```

#### 3. 内存泄漏

```java
// 问题：忘记关闭客户端
LogXClient client = LogXClient.builder()...build();

// 解决：添加 shutdown hook
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    client.shutdown();
}));
```

---

## 总结

LogX SDK 提供了：
- ✅ **简单易用**: 3行代码即可集成
- ✅ **高性能**: gRPC + 缓冲 + 批量
- ✅ **灵活**: HTTP/gRPC 双协议
- ✅ **智能**: AOP 自动拦截 + 用户上下文
- ✅ **可靠**: 重试机制 + 异常处理
- ✅ **可扩展**: Protobuf Struct 支持任意字段

**下一步**: 查看 [Engine 模块文档](./LogX-Engine-Guide.md) 了解日志处理流程
