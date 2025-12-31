# LogX Gateway 模块技术文档

## 📑 目录

- [模块概述](#模块概述)
- [HTTP网关](#http网关)
- [gRPC网关](#grpc网关)
- [限流机制](#限流机制)
- [认证授权](#认证授权)
- [性能对比](#性能对比)

---

## 模块概述

### 核心功能

```
Gateway模块负责：
├── HTTP接入         # LogIngestController + LogIngestService
├── gRPC接入         # LogIngestGrpcService
├── 三级限流         # RateLimiterService
├── API Key认证      # GrpcAuthInterceptor
├── Kafka发送        # KafkaLogSender
└── 租户隔离         # TenantContext
```

### 数据流

```
SDK → HTTP/gRPC → 限流检查 → 认证校验 → Kafka(logx-logs) → Processor
```

---

## HTTP网关

### 1. HTTP控制器 (LogIngestController)

#### 接口列表

| 接口 | 方法 | 说明 | 限流 |
|------|------|------|------|
| `/api/v1/log` | POST | 接收单条日志 | ✓ |
| `/api/v1/logs` | POST | 批量接收日志 | ✓ |
| `/api/v1/health` | GET | 健康检查 | ✗ |

#### 单条日志接收

```java
@PostMapping("/log")
public Result<Void> ingestLog(@Valid @RequestBody LogDTO logDTO) {
    log.debug("接收日志: {}", logDTO.getMessage());
    ingestService.ingest(logDTO);
    return Result.success();
}
```

**请求示例**:
```bash
curl -X POST http://localhost:10240/api/v1/log \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: company_a" \
  -H "X-System-Id: erp_system" \
  -H "X-API-Key: sk_test_key_001" \
  -d '{
    "level": "INFO",
    "message": "用户登录成功",
    "userId": "user123",
    "userName": "张三",
    "module": "认证模块",
    "timestamp": "2024-12-27T10:30:00"
  }'
```

**响应**:
```json
{
  "success": true,
  "code": 200,
  "message": "success"
}
```

#### 批量日志接收

```java
@PostMapping("/logs")
public Result<Map<String, Object>> ingestLogs(@RequestBody List<@Valid LogDTO> logs) {
    log.debug("批量接收日志: {} 条", logs.size());
    return ingestService.ingestBatch(logs);
}
```

**请求示例**:
```bash
curl -X POST http://localhost:10240/api/v1/logs \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: company_a" \
  -H "X-System-Id: erp_system" \
  -H "X-API-Key: sk_test_key_001" \
  -d '[
    {
      "level": "INFO",
      "message": "日志1"
    },
    {
      "level": "WARN",
      "message": "日志2"
    }
  ]'
```

**响应**:
```json
{
  "success": true,
  "code": 200,
  "data": {
    "totalCount": 2,
    "successCount": 2,
    "failCount": 0,
    "errors": []
  }
}
```

---

### 2. HTTP服务层 (LogIngestService)

#### 核心流程

```java
public void ingest(LogDTO logDTO) {
    // 1. 补充元数据
    enrichLog(logDTO);
    
    // 2. 发送到Kafka
    sendToKafka(logDTO);
}
```

#### 元数据补充

```java
private void enrichLog(LogDTO logDTO) {
    // 1. 生成ID（如果没有）
    if (logDTO.getId() == null) {
        logDTO.setId(UUID.randomUUID().toString().replace("-", ""));
    }
    
    // 2. 设置租户ID（从ThreadLocal获取）
    if (logDTO.getTenantId() == null) {
        logDTO.setTenantId(TenantContext.getTenantId());
    }
    
    // 3. 设置时间戳（默认当前时间）
    if (logDTO.getTimestamp() == null) {
        logDTO.setTimestamp(LocalDateTime.now());
    }
}
```

#### 批量接收实现

```java
public Result<Map<String, Object>> ingestBatch(List<LogDTO> logs) {
    if (logs == null || logs.isEmpty()) {
        throw new BusinessException("日志列表不能为空");
    }
    
    int successCount = 0;
    int failCount = 0;
    List<String> errors = new ArrayList<>();
    
    // 逐条处理
    for (int i = 0; i < logs.size(); i++) {
        try {
            ingest(logs.get(i));
            successCount++;
        } catch (Exception e) {
            failCount++;
            errors.add("Index " + i + ": " + e.getMessage());
            log.error("无法摄取index{}处的日志", i, e);
        }
    }
    
    log.info("批量接收完成: 总数={}, 成功={}, 失败={}",
            logs.size(), successCount, failCount);
    
    // 返回统计结果
    Map<String, Object> result = new HashMap<>();
    result.put("totalCount", logs.size());
    result.put("successCount", successCount);
    result.put("failCount", failCount);
    result.put("errors", errors);
    
    return Result.success(result);
}
```

**批量处理特点**:
- 单条失败不影响其他日志
- 详细的错误信息记录
- 返回完整的统计结果

#### Kafka发送

```java
private void sendToKafka(LogDTO logDTO) {
    try {
        String json = JsonUtil.toJson(logDTO);
        String topic = SystemConstant.KAFKA_TOPIC_LOGS; // "logx-logs"
        String key = generateKey(logDTO);
        
        kafkaTemplate.send(topic, key, json);
        log.debug("日志已发送到 Kafka: {}", logDTO.getId());
    } catch (Exception e) {
        log.error("发送日志到 Kafka 失败", e);
        throw new BusinessException("日志接收失败");
    }
}
```

#### Kafka Key生成

```java
private String generateKey(LogDTO log) {
    String tenantId = String.valueOf(log.getTenantId());
    String systemId = String.valueOf(log.getSystemId());
    String traceId = log.getTraceId();
    
    StringBuilder key = new StringBuilder();
    if (tenantId != null) {
        key.append(tenantId);
    }
    key.append(":");
    if (systemId != null) {
        key.append(systemId);
    }
    key.append(":");
    if (traceId != null) {
        key.append(traceId);
    }
    
    return key.toString();
}
```

**Key格式示例**:
```
tenant_001:sys_erp:a1b2c3d4e5f6
tenant_001:sys_crm:
:sys_test:trace123
```

**Key作用**:
- 保证相同租户/系统的日志在同一分区
- 便于后续按租户/系统处理
- 支持分布式追踪（traceId）
- 支持消息的顺序性保证

---

## gRPC网关

### 1. gRPC服务 (LogIngestGrpcService)

#### 服务定义

```protobuf
service LogService {
  // 批量接收日志
  rpc SendLogs (LogBatchRequest) returns (LogBatchResponse);
  
  // 流式接收日志（客户端流）
  rpc StreamLogs (stream LogEntry) returns (LogBatchResponse);
}
```

#### 批量接收实现

```java
@Override
public void sendLogs(LogBatchRequest request, StreamObserver<LogBatchResponse> responseObserver) {
    // 1. 参数校验
    if (request.getLogsList().isEmpty()) {
        // 返回错误响应
        return;
    }
    
    // 2. 检查批次大小
    if (logCount > maxBatchSize) {
        // 超过限制
        return;
    }
    
    // 3. 转换为Map格式
    List<Map<String, Object>> logs = request.getLogsList().stream()
        .map(this::convertToMap)
        .collect(Collectors.toList());
    
    // 4. 发送到Kafka
    int successCount = kafkaLogSender.sendBatch(logs);
    
    // 5. 构建响应
    LogBatchResponse response = LogBatchResponse.newBuilder()
        .setSuccess(successCount > 0)
        .setReceived(logCount)
        .setSuccessCount(successCount)
        .setFailedCount(failedCount)
        .build();
    
    responseObserver.onNext(response);
    responseObserver.onCompleted();
}
```

#### 流式接收实现

```java
@Override
public StreamObserver<LogEntry> streamLogs(StreamObserver<LogBatchResponse> responseObserver) {
    return new StreamObserver<>() {
        private int received = 0;
        private int success = 0;
        
        @Override
        public void onNext(LogEntry logEntry) {
            received++;
            try {
                Map<String, Object> log = convertToMap(logEntry);
                if (kafkaLogSender.send(log)) {
                    success++;
                }
            } catch (Exception e) {
                log.error("处理流式日志失败", e);
            }
        }
        
        @Override
        public void onCompleted() {
            LogBatchResponse response = LogBatchResponse.newBuilder()
                .setSuccess(success > 0)
                .setReceived(received)
                .setSuccessCount(success)
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    };
}
```

---

### 2. LogEntry转换 (Protobuf → Map)

#### 完整字段映射

```java
private Map<String, Object> convertToMap(LogEntry entry) {
    Map<String, Object> map = new HashMap<>();
    
    // 基础字段
    if (!entry.getTraceId().isEmpty()) {
        map.put("traceId", entry.getTraceId());
    }
    
    // 时间戳处理（毫秒）
    if (entry.getTimestamp() > 0) {
        map.put("timestamp", entry.getTimestamp());
    } else {
        map.put("timestamp", Instant.now().toEpochMilli());
    }
    
    // 日志内容
    if (!entry.getMessage().isEmpty()) {
        map.put("message", entry.getMessage());
    }
    
    // 标签列表
    if (!entry.getTagsList().isEmpty()) {
        map.put("tags", entry.getTagsList());
    }
    
    // 扩展字段（Protobuf Struct → Map）
    if (entry.hasExtra()) {
        Map<String, Object> extra = structToMap(entry.getExtra());
        if (!extra.isEmpty()) {
            map.put("extra", extra);
        }
    }
    
    return map;
}
```

#### Protobuf Struct转换

```java
private Map<String, Object> structToMap(Struct struct) {
    if (struct == null || struct.getFieldsCount() == 0) {
        return new HashMap<>();
    }
    
    Map<String, Object> map = new HashMap<>();
    struct.getFieldsMap().forEach((key, value) -> 
        map.put(key, valueToObject(value))
    );
    
    return map;
}

private Object valueToObject(Value value) {
    return switch (value.getKindCase()) {
        case NULL_VALUE -> null;
        case NUMBER_VALUE -> value.getNumberValue();
        case STRING_VALUE -> value.getStringValue();
        case BOOL_VALUE -> value.getBoolValue();
        case STRUCT_VALUE -> structToMap(value.getStructValue());
        case LIST_VALUE -> {
            List<Object> list = new ArrayList<>();
            for (Value item : value.getListValue().getValuesList()) {
                list.add(valueToObject(item));
            }
            yield list;
        }
        default -> null;
    };
}
```

**支持的数据类型**:
- ✅ null
- ✅ 数字（转为double）
- ✅ 字符串
- ✅ 布尔值
- ✅ 对象（嵌套Map）
- ✅ 数组（List）

---

### 3. Kafka发送器 (KafkaLogSender)

#### 单条发送

```java
public boolean send(Map<String, Object> logOne) {
    try {
        String logJson = JsonUtil.toJson(logOne);
        String key = generateKey(logOne);
        
        kafkaTemplate.send(logTopic, key, logJson)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("发送失败", ex);
                }
            });
        
        return true;
    } catch (Exception e) {
        log.error("发送失败", e);
        return false;
    }
}
```

#### 批量发送

```java
public int sendBatch(List<Map<String, Object>> logs) {
    if (logs == null || logs.isEmpty()) {
        return 0;
    }
    
    AtomicInteger successCount = new AtomicInteger(0);
    
    // 1. 准备所有发送任务
    List<CompletableFuture<SendResult<String, String>>> futures = logs.stream()
        .map(logOne -> {
            String logJson = JsonUtil.toJson(logOne);
            String key = generateKey(logOne);
            return kafkaTemplate.send(logTopic, key, logJson);
        })
        .toList();
    
    // 2. 等待所有任务完成（30秒超时）
    try {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(30, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        log.error("批量发送超时", e);
    }
    
    // 3. 统计成功数量
    for (CompletableFuture<SendResult<String, String>> future : futures) {
        if (future.isDone() && !future.isCompletedExceptionally()) {
            successCount.incrementAndGet();
        }
    }
    
    return successCount.get();
}
```

**性能特点**:
- 并发发送，不阻塞
- 30秒超时保护
- 异步统计成功率

---

## 限流机制

### 1. 三级限流架构

```
请求 → 全局限流 → 租户限流 → 系统限流 → 通过
        (10000/s)   (1000/s)    (5000/min)
```

### 2. 限流服务 (RateLimiterService)

#### 全局限流

```java
public boolean checkGlobalLimit() {
    String key = "rate_limit:global:" + getCurrentMinute();
    // 10000 QPS = 600000 QPM
    boolean allowed = redisRateLimiter.tryAcquire(key, globalQps * 60, 60);
    
    if (!allowed) {
        log.warn("超出全局速率限制，当前分钟: {}", getCurrentMinute());
    }
    
    return allowed;
}
```

**限流参数**:
```yaml
logx:
  rate-limit:
    global:
      qps: 10000  # 全局每秒10000次
```

#### 租户限流

```java
public boolean checkTenantLimit(String tenantId) {
    String key = "rate_limit:tenant:" + tenantId + ":" + getCurrentMinute();
    // 1000 QPS = 60000 QPM
    boolean allowed = redisRateLimiter.tryAcquire(key, tenantQps * 60, 60);
    
    if (!allowed) {
        log.warn("超出租户限流，租户ID: {}", tenantId);
    }
    
    return allowed;
}
```

**限流参数**:
```yaml
logx:
  rate-limit:
    tenant:
      qps: 1000  # 每租户每秒1000次
```

#### 系统限流

```java
public boolean checkSystemLimit(String tenantId, String systemId) {
    String key = "rate_limit:system:" + tenantId + ":" + systemId + ":" + getCurrentMinute();
    // 5000 QPM
    boolean allowed = redisRateLimiter.tryAcquire(key, systemQpm, 60);
    
    if (!allowed) {
        log.warn("超出系统限流，tenantId: {}, systemId: {}", tenantId, systemId);
    }
    
    return allowed;
}
```

**限流参数**:
```yaml
logx:
  rate-limit:
    system:
      qpm: 5000  # 每系统每分钟5000次
```

#### 综合检查

```java
public void checkRateLimit(String tenantId, String systemId) {
    // 1. 全局限流检查
    if (!checkGlobalLimit()) {
        throw new BusinessException("系统繁忙，请稍后重试");
    }
    
    // 2. 租户限流检查
    if (!checkTenantLimit(tenantId)) {
        throw new BusinessException("租户请求过于频繁");
    }
    
    // 3. 系统限流检查
    if (!checkSystemLimit(tenantId, systemId)) {
        throw new BusinessException("系统请求过于频繁");
    }
}
```

---

### 3. gRPC限流拦截器 (GrpcRateLimitInterceptor)

```java
@GrpcGlobalServerInterceptor
@Order(100) // 在认证拦截器之后执行
public class GrpcRateLimitInterceptor implements ServerInterceptor {
    
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        // 如果限流未启用，直接放行
        if (!rateLimitEnabled) {
            return next.startCall(call, headers);
        }
        
        String tenantId = TenantContext.getTenantId();
        String systemId = TenantContext.getSystemId();
        
        // 三级限流检查
        if (!checkGlobalLimit()) {
            call.close(Status.RESOURCE_EXHAUSTED
                .withDescription("系统繁忙"), headers);
            return new ServerCall.Listener<>() {};
        }
        
        if (!checkTenantLimit(tenantId)) {
            call.close(Status.RESOURCE_EXHAUSTED
                .withDescription("租户请求过于频繁"), headers);
            return new ServerCall.Listener<>() {};
        }
        
        if (!checkSystemLimit(tenantId, systemId)) {
            call.close(Status.RESOURCE_EXHAUSTED
                .withDescription("系统请求过于频繁"), headers);
            return new ServerCall.Listener<>() {};
        }
        
        // 通过限流检查
        return next.startCall(call, headers);
    }
}
```

---

## 认证授权

### 1. gRPC认证拦截器 (GrpcAuthInterceptor)

#### 认证流程

```java
@Override
public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(...) {
    // 1. 提取认证信息
    String apiKey = headers.get(API_KEY_METADATA_KEY);
    String tenantId = headers.get(TENANT_ID_METADATA_KEY);
    String systemId = headers.get(SYSTEM_ID_METADATA_KEY);
    
    // 2. 验证必填字段
    if (apiKey == null || tenantId == null || systemId == null) {
        call.close(Status.UNAUTHENTICATED.withDescription("Missing credentials"), headers);
        return new ServerCall.Listener<>() {};
    }
    
    // 3. 验证API Key
    if (!validateApiKey(apiKey, tenantId, systemId)) {
        call.close(Status.PERMISSION_DENIED.withDescription("Invalid API Key"), headers);
        return new ServerCall.Listener<>() {};
    }
    
    // 4. 设置租户上下文
    TenantContext.setTenantId(tenantId);
    TenantContext.setSystemId(systemId);
    
    // 5. 继续处理，完成后清理上下文
    return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(
        next.startCall(call, headers)) {
        @Override
        public void onComplete() {
            try {
                super.onComplete();
            } finally {
                TenantContext.clear();
            }
        }
    };
}
```

#### Metadata Keys

```java
private static final Metadata.Key<String> API_KEY_METADATA_KEY =
    Metadata.Key.of("X-Api-Key", Metadata.ASCII_STRING_MARSHALLER);

private static final Metadata.Key<String> TENANT_ID_METADATA_KEY =
    Metadata.Key.of("X-Tenant-Id", Metadata.ASCII_STRING_MARSHALLER);

private static final Metadata.Key<String> SYSTEM_ID_METADATA_KEY =
    Metadata.Key.of("X-System-Id", Metadata.ASCII_STRING_MARSHALLER);
```

#### API Key验证

```java
private boolean validateApiKey(String apiKey, String tenantId, String systemId) {
    // 查询数据库验证
    int count = validateMapper.validateApiKey(apiKey, tenantId, systemId);
    return count >= 1;
}
```

**SQL查询**:
```sql
SELECT count(*)
FROM sys_system
WHERE api_key = #{apiKey}
  AND tenant_id = #{tenantId}
  AND system_id = #{systemId}
```

---

## 性能对比

### HTTP vs gRPC

| 指标 | HTTP | gRPC | 提升 |
|------|------|------|------|
| **吞吐量** | 6,578 QPS | 14,705 QPS | **2.2x** |
| **传输大小** | 100% | 67% | **-33%** |
| **延迟（P50）** | 150ms | 68ms | **-55%** |
| **延迟（P99）** | 500ms | 200ms | **-60%** |
| **CPU占用** | 45% | 30% | **-33%** |

**测试条件**:
- 10万条日志
- 单机测试
- 平均日志大小: 2KB

### 批量 vs 流式（gRPC）

| 模式 | 适用场景 | 性能 |
|------|---------|------|
| **Batch** | 固定批次 | 14,705 QPS |
| **Stream** | 持续发送 | 18,000 QPS |

**建议**:
- 批量发送（< 1000条）→ Batch模式
- 大量持续发送 → Stream模式

---

## 配置指南

### 完整配置

```yaml
server:
  port: 10240  # HTTP端口

# gRPC配置
grpc:
  server:
    port: 10241  # gRPC端口

# LogX配置
logx:
  # 限流配置
  rate-limit:
    enabled: true
    global:
      qps: 10000     # 全局每秒10000次
    tenant:
      qps: 1000      # 每租户每秒1000次
    system:
      qpm: 5000      # 每系统每分钟5000次
  
  # 批量配置
  batch:
    max-size: 100    # 最大批次大小
  
  # Kafka配置
  kafka:
    topic:
      log-ingestion: logx-logs

# Kafka配置
spring:
  kafka:
    bootstrap-servers: localhost:29092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: 1
      retries: 3
```

---

## 最佳实践

### 1. 选择合适的接入方式

**HTTP** 适用于：
- 偶发日志记录
- 低频场景（< 1000 QPS）
- 调试和测试

**gRPC** 适用于：
- 高频日志记录
- 大批量数据（> 1000 QPS）
- 生产环境

### 2. 批量大小建议

```java
// HTTP批量
List<LogDTO> logs = new ArrayList<>();
for (int i = 0; i < 50; i++) {  // 推荐50-100条
    logs.add(createLog());
}
httpClient.sendBatch(logs);

// gRPC批量
List<LogEntry> logs = new ArrayList<>();
for (int i = 0; i < 100; i++) {  // 推荐100-500条
    logs.add(createLog());
}
grpcClient.sendBatch(logs);
```

### 3. 错误处理

```java
try {
    client.send(log);
} catch (BusinessException e) {
    if (e.getMessage().contains("限流")) {
        // 等待后重试
        Thread.sleep(1000);
        client.send(log);
    } else if (e.getMessage().contains("认证")) {
        // 检查API Key
        log.error("认证失败，请检查API Key");
    } else {
        // 其他错误
        log.error("发送失败", e);
    }
}
```

---

## 故障排查

### 1. 限流问题

**现象**: 请求返回 "系统繁忙"

**排查**:
```bash
# 检查Redis限流key
redis-cli KEYS "rate_limit:*"

# 查看剩余配额
redis-cli GET "rate_limit:global:xxx"
```

**解决**:
```yaml
# 调整限流配置
logx:
  rate-limit:
    global:
      qps: 20000  # 增加全局限流
```

### 2. 认证失败

**现象**: gRPC返回 "Invalid API Key"

**排查**:
```sql
-- 检查API Key
SELECT * FROM sys_system
WHERE api_key = 'your-api-key';
```

**解决**:
```bash
# 检查请求头
X-Api-Key: sk_test_key_001
X-Tenant-Id: company_a
X-System-Id: erp_system
```

---

## 下一步

- 查看 [SDK详解](./LogX-SDK-Guide.md) 了解客户端集成
- 查看 [Engine详解](./LogX-Engine-Guide.md) 了解日志处理
- 查看 [测试指南](./LogX-Testing-Guide.md) 学习测试用例
