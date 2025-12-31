# LogX Engine 模块技术文档

## 📑 目录

- [模块概述](#模块概述)
- [日志处理流程](#日志处理流程)
- [核心组件](#核心组件)
- [数据脱敏](#数据脱敏)
- [性能优化](#性能优化)
- [监控指标](#监控指标)

---

## 模块概述

### 架构图

```
SDK → Gateway → Kafka(logx-logs) → Processor → Elasticsearch
                                        ↓
                                   Kafka(logx-logs-processing) → Detection
```

### 处理流程

```
1. Kafka Consumer 批量拉取日志 (500条/批)
2. LogParser 解析 + 脱敏 + 标准化
3. ElasticsearchWriter 批量写入
4. 转发到 Detection 模块 (异常检测)
5. 提交 Offset
```

### 模块组成

```
logx-engine/
├── logx-engine-processor/      # 日志处理器 (核心)
│   ├── consumer/
│   │   └── LogKafkaConsumer.java      # Kafka消费者
│   ├── parser/
│   │   └── LogParser.java             # 日志解析器
│   └── writer/
│       └── ElasticsearchWriter.java   # ES写入器
│
├── logx-engine-storage/        # 存储管理
│   ├── elasticsearch/
│   │   ├── EsIndexManager.java        # 索引管理
│   │   └── EsTemplateManager.java     # 模板管理
│   ├── lifecycle/              # 生命周期管理
│   └── archive/                # 归档服务
│
└── logx-engine-detection/      # 异常检测
    ├── rule/                   # 规则引擎
    ├── analyzer/               # 分析器
    └── alert/                  # 告警触发
```

---

## 日志处理流程

### 1. Kafka 消费

**LogKafkaConsumer.java** - 批量消费日志

#### 配置

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:29092
    consumer:
      group-id: logx-processor-group
      auto-offset-reset: latest
      enable-auto-commit: false    # 手动提交
      max-poll-records: 500        # 批量大小
      concurrency: 3               # 并发消费者

logx:
  kafka:
    topic:
      log-ingestion: logx-logs              # 输入Topic
      log-processing: logx-logs-processing  # 输出Topic(给Detection)
      dead-letter: logx-logs-dlq            # 死信队列
  consumer:
    max-retries: 3                 # 最大重试次数
    retry-backoff-ms: 1000         # 重试间隔
```

#### 核心代码

```java

@KafkaListener(
        topics = "${logx.kafka.topic.log-ingestion:logx-logs}",
        groupId = "${spring.kafka.consumer.group-id:logx-processor-group}",
        containerFactory = "kafkaListenerContainerFactory"
)
public void consumeLogs(List<String> messages, Acknowledgment acknowledgment) {
    // 1. 批量解析日志
    ParseResult parseResult = parseMessages(messages);

    // 2. 批量写入 ES (带重试)
    boolean writeSuccess = writeWithRetry(parseResult.validLogs);

    // 3. 转发到 Detection 模块
    boolean forwardSuccess = forwardToDetection(parseResult.validLogs);

    // 4. 全部成功才提交 offset
    if (writeSuccess && forwardSuccess) {
        acknowledgment.acknowledge();
    } else {
        // 失败的发送到死信队列
        sendToDeadLetterQueue(messages, "写入ES或转发失败");
        acknowledgment.acknowledge();  // 避免阻塞
    }
}
```

#### 关键特性

| 特性       | 说明           | 实现                         |
|----------|--------------|----------------------------|
| **批量消费** | 500条/批       | `max-poll-records=500`     |
| **手动提交** | 处理成功才提交      | `enable-auto-commit=false` |
| **重试机制** | 指数退避         | 1s → 2s → 4s               |
| **死信队列** | 失败消息保存       | `logx-logs-dlq`            |
| **转发机制** | 发送到Detection | `logx-logs-processing`     |

---

### 2. 日志解析

**LogParser.java** - 解析、标准化、脱敏

#### 处理步骤

```java
public Map<String, Object> parse(String logJson) {
    // 1. JSON 解析
    Map<String, Object> logMap = JsonUtil.parseObject(logJson);

    // 2. 标准化处理
    Map<String, Object> normalized = normalize(logMap);

    // 3. 敏感信息脱敏
    desensitizeEnhanced(normalized);

    // 4. 字段补全
    fillMissingFields(normalized);

    // 5. 字段验证
    validateFields(normalized);

    return normalized;
}
```

#### 字段标准化

支持多种字段名变体：

```java
// 示例：支持驼峰和下划线
normalized.put("traceId",getString(logMap, "traceId","trace_id"));
        normalized.

put("className",getString(logMap, "className","class_name"));
        normalized.

put("requestUrl",getString(logMap, "requestUrl","request_url","url"));
```

**支持的字段别名**:

| 标准字段          | 支持的别名                   |
|---------------|-------------------------|
| traceId       | trace_id                |
| spanId        | span_id                 |
| className     | class_name              |
| methodName    | method_name             |
| lineNumber    | line_number             |
| requestUrl    | request_url, url        |
| requestMethod | request_method, method  |
| responseTime  | response_time, duration |

#### 日志级别标准化

```java
private String normalizeLevel(String level) {
    return switch (level.toUpperCase()) {
        case "WARN", "WARNING" -> "WARN";
        case "ERR", "ERROR", "SEVERE" -> "ERROR";
        case "FATAL", "CRITICAL" -> "FATAL";
        case "TRACE", "FINEST" -> "TRACE";
        case "DEBUG", "FINE" -> "DEBUG";
        default -> "INFO";
    };
}
```

#### 时间戳解析

支持多种格式：

```java
// 支持的时间戳格式
private static final List<DateTimeFormatter> TIMESTAMP_FORMATTERS = Arrays.asList(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,           // 2024-12-27T10:30:00
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,          // 2024-12-27T10:30:00+08:00
        DateTimeFormatter.ISO_ZONED_DATE_TIME,           // 2024-12-27T10:30:00+08:00[Asia/Shanghai]
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
);

// 自动处理
-Long:

毫秒时间戳(13位)
-Integer:

秒时间戳(10位)
-LocalDateTime:直接使用
-String:尝试多种格式解析
```

---

### 3.数据脱敏

#### 脱敏规则

LogParser 自动对敏感信息进行脱敏：

| 类型      | 正则表达式             | 脱敏规则     | 示例                  |
|---------|-------------------|----------|---------------------|
| **手机号** | `1[3-9]\d{9}`     | 保留前3后4   | 138****5678         |
| **身份证** | `\d{17}[\dXx]`    | 保留前3后4   | 310***********1234  |
| **邮箱**  | `[^@]+@[^.]+\..+` | 保留首字符和域名 | u***@example.com    |
| **银行卡** | `\d{13,19}`       | 保留前4后4   | 6222 **** **** 1234 |
| **用户名** | -                 | 保留第一个字符  | 张**                 |

#### 脱敏字段

```java
// 自动脱敏的字段
-message         // 日志消息
-requestParams   // 请求参数
-exception       // 异常堆栈
-userName        // 用户名 (保留姓)
```

#### 脱敏示例

**原始日志**:

```json
{
  "message": "用户登录，手机号：13812345678，邮箱：user@example.com",
  "requestParams": "{\"idCard\":\"310123199001011234\"}",
  "userName": "张三丰"
}
```

**脱敏后**:

```json
{
  "message": "用户登录，手机号：138****5678，邮箱：u***@example.com",
  "requestParams": "{\"idCard\":\"310***********1234\"}",
  "userName": "张**"
}
```

#### 敏感字段过滤

```java
// extra 字段中的敏感 key 会被替换为 "***"
Set<String> sensitiveKeys = Set.of(
        "password", "pwd", "token", "secret", "key",
        "authorization", "auth", "apiKey", "api_key"
);

// 示例
原始:{"password":"abc123","amount":100}
过滤:{"password":"***","amount":100}
```

---

### 4. Elasticsearch 写入

**ElasticsearchWriter.java** - 批量写入、自动创建索引

#### 核心特性

| 特性       | 说明        | 配置             |
|----------|-----------|----------------|
| **批量写入** | 减少网络开销    | `max-size=500` |
| **自动分批** | 超过阈值自动分割  | 自动处理           |
| **索引缓存** | 避免重复检查    | 内存缓存           |
| **幂等写入** | 使用ID防重复   | traceId+spanId |
| **自动创建** | 索引不存在自动创建 | 自动处理           |

#### 索引命名规则

```java
// 格式: logx-logs-{tenantId}-{systemId}-{yyyy.MM.dd}
generateIndexName(log):
tenantId =

sanitizeIndexComponent(log.get("tenantId"), "default")
systemId =

sanitizeIndexComponent(log.get("systemId"), "unknown")
date =

extractDate(log)  // yyyy.MM.dd
    
    return"logx-logs-"+tenantId +"-"+systemId +"-"+
        date

// 示例
                tenantId = company_a, systemId = erp_system, date = 2024.12.27
        →logx-logs-company_a-erp_system-2024.12.27
```

#### 安全防护

```java
// 1. 索引名称清理 (防注入)
private String sanitizeIndexComponent(String input, String defaultValue) {
    // 转小写
    String sanitized = input.toLowerCase().trim();

    // 只保留字母、数字、连字符
    sanitized = sanitized.replaceAll("[^a-z0-9-]", "");

    // 限制长度
    if (sanitized.length() > 50) {
        sanitized = sanitized.substring(0, 50);
    }

    return sanitized;
}

// 2. 索引名称长度限制
MAX_INDEX_NAME_LENGTH =200  // ES限制255

// 3. 正则验证
SAFE_NAME_PATTERN ="^[a-z0-9-]+$"
```

#### 批量写入流程

```java
public int bulkWrite(List<Map<String, Object>> logs) {
    // 1. 分批处理 (500条/批)
    List<List<Map<String, Object>>> batches = splitIntoBatches(logs, maxBulkSize);

    for (List<Map<String, Object>> batch : batches) {
        // 2. 预先确保索引存在
        Set<String> requiredIndices = extractIndices(batch);
        ensureIndicesExist(requiredIndices);

        // 3. 构建批量请求
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (Map<String, Object> log : batch) {
            String indexName = generateIndexName(log);
            String documentId = extractDocumentId(log);  // 幂等性

            bulkBuilder.operations(op -> op
                    .index(idx -> idx
                            .index(indexName)
                            .id(documentId)
                            .document(log)
                    )
            );
        }

        // 4. 执行批量写入
        BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());

        // 5. 处理结果
        processBulkResponse(response, batch.size());
    }
}
```

#### 文档ID生成 (幂等性)

```java
private String extractDocumentId(Map<String, Object> log) {
    // 1. 优先使用日志ID
    Object id = log.get("id");
    if (id != null) return id.toString();

    // 2. 使用 traceId + spanId 组合 (推荐)
    String traceId = (String) log.get("traceId");
    String spanId = (String) log.get("spanId");
    if (traceId != null && spanId != null) {
        return traceId + "-" + spanId;
    }

    // 3. 最后的 fallback
    return System.currentTimeMillis() + "-" + (int) (Math.random() * 10000);
}
```

**幂等性保证**: 相同 traceId+spanId 的日志不会重复插入

#### 索引自动创建

```java
private void ensureIndicesExist(Set<String> indexNames) {
    for (String indexName : indexNames) {
        // 1. 检查缓存
        if (indexExistenceCache.get(indexName)) {
            continue;
        }

        // 2. 检查ES
        boolean exists = checkIndexExists(indexName);

        // 3. 不存在则创建
        if (!exists) {
            IndexInfo info = parseIndexName(indexName);
            esIndexManager.createLogIndex(
                    info.tenantId,
                    info.systemId,
                    info.date
            );
        }

        // 4. 更新缓存
        indexExistenceCache.put(indexName, true);
    }
}
```

---

### 5. 转发到 Detection

**目的**: 将日志转发到异常检测模块

```java
private boolean forwardToDetection(List<Map<String, Object>> logs) {
    List<CompletableFuture<?>> futures = new ArrayList<>();

    for (Map<String, Object> log : logs) {
        String logJson = JsonUtil.toJson(log);
        String key = generateKey(log);  // tenantId:systemId:traceId

        CompletableFuture<?> future = kafkaTemplate.send(
                "logx-logs-processing",  // Topic
                key,                      // Key (分区)
                logJson                   // Value
        );

        futures.add(future);
    }

    // 等待所有发送完成
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(30, TimeUnit.SECONDS);

    return allSuccess;
}
```

**Key生成规则**: `{tenantId}:{systemId}:{traceId}`

- 保证相同租户/系统的日志在同一分区
- 便于Detection模块按租户处理

---

## 核心组件

### 1. 重试机制

#### 指数退避

```java
private boolean writeWithRetry(List<Map<String, Object>> logs) {
    int retryCount = 0;

    while (retryCount <= maxRetries) {
        try {
            int successCount = elasticsearchWriter.bulkWrite(logs);
            if (successCount == logs.size()) {
                return true;  // 全部成功
            }
        } catch (Exception e) {
            log.error("写入ES失败，重试{}/{}", retryCount, maxRetries);
        }

        // 指数退避: 1s → 2s → 4s
        if (retryCount < maxRetries) {
            retryCount++;
            long backoff = retryBackoffMs * (1L << (retryCount - 1));
            long actualBackoff = Math.min(backoff, 10000);  // 最多10s
            Thread.sleep(actualBackoff);
        } else {
            break;
        }
    }

    return false;
}
```

**重试策略**:

- 最大重试次数: 3次
- 退避间隔: 1s → 2s → 4s
- 最大等待: 10s

---

### 2. 死信队列

#### 用途

失败的消息发送到死信队列，避免丢失：

```java
private void sendToDeadLetterQueue(List<String> messages, String reason) {
    for (String message : messages) {
        kafkaTemplate.send(
                "logx-logs-dlq",  // 死信Topic
                reason,           // Key (失败原因)
                message           // Value (原始消息)
        );
    }

    log.info("已将{}/{}消息发送到死信队列：{}",
            successCount, messages.size(), reason);
}
```

**失败原因**:

- 解析失败
- 写入ES失败
- 转发失败
- 异常错误

**后续处理**:

- 人工审查
- 重新处理
- 数据分析

---

### 3. 索引存在性缓存

```java
// 内存缓存，避免频繁检查ES
private final Map<String, Boolean> indexExistenceCache = new ConcurrentHashMap<>();

// 使用
if(indexExistenceCache.

getOrDefault(indexName, false)){
        return;  // 缓存命中，跳过检查
        }

boolean exists = checkIndexExists(indexName);
indexExistenceCache.

put(indexName, true);  // 更新缓存
```

**优点**:

- 减少ES查询
- 提高性能
- 线程安全 (ConcurrentHashMap)

**缓存失效**:

```java
// 1. 写入失败时清空缓存
if(e.getMessage().

contains("all shards failed")){
        indexExistenceCache.

clear();
}

// 2. 手动清空
public void clearIndexCache() {
    indexExistenceCache.clear();
}
```

---

## 性能优化

### 1. 批量处理

| 阶段      | 批量大小 | 说明                      |
|---------|------|-------------------------|
| Kafka消费 | 500  | `max-poll-records`      |
| ES写入    | 500  | `logx.es.bulk.max-size` |
| Kafka转发 | 异步批量 | CompletableFuture       |

### 2. 并发配置

```yaml
spring:
  kafka:
    consumer:
      concurrency: 3  # 3个并发消费者

logx:
  storage:
    bulk:
      concurrent-requests: 2  # 2个并发写入
```

**吞吐量估算**:

```
单消费者: 500条/次 × 2次/秒 = 1000条/秒
3个消费者: 1000 × 3 = 3000条/秒
```

### 3. 内存优化

```java
// 1. 使用对象池 (如果需要)
// 2. 及时释放大对象
List<Map<String, Object>> logs = ...;
        elasticsearchWriter.

bulkWrite(logs);
logs.

clear();  // 释放内存

// 3. 限制批量大小
MAX_BULK_SIZE =500  // 防止OOM
```

### 4. 网络优化

```yaml
# Kafka优化
spring:
  kafka:
    consumer:
      fetch-min-size: 1024      # 最小拉取1KB
      fetch-max-wait: 500       # 最大等待500ms

# ES优化
logx:
  storage:
    bulk:
      flush-interval: 5m        # 5分钟刷新一次
```

---

## 监控指标

### 1. Micrometer 指标

```java
private void recordMetrics(int successCount, int failCount) {
    // 成功计数
    meterRegistry.counter("logx.kafka.consumer.success",
                    "tenant", String.valueOf(TenantContext.getTenantId()))
            .increment(successCount);

    // 失败计数
    meterRegistry.counter("logx.kafka.consumer.failed",
                    "tenant", String.valueOf(TenantContext.getTenantId()))
            .increment(failCount);

    // 批量大小
    meterRegistry.gauge("logx.kafka.consumer.last.batch.size", successCount);
}
```

### 2. 关键指标

| 指标                                    | 类型      | 说明     | 告警阈值   |
|---------------------------------------|---------|--------|--------|
| `logx.kafka.consumer.success`         | Counter | 成功处理数  | -      |
| `logx.kafka.consumer.failed`          | Counter | 失败处理数  | >100   |
| `logx.kafka.consumer.last.batch.size` | Gauge   | 最近批量大小 | -      |
| `logx.kafka.lag`                      | Gauge   | 消费延迟   | >10000 |
| `logx.es.write.duration`              | Timer   | 写入耗时   | >100ms |

### 3. 日志监控

```java
// 处理完成日志
log.info("已处理 {} 个日志：{} 个有效，{} 个解析失败，耗时 {} 毫秒",
         messages.size(),validLogs.

size(),failedMessages.

size(),duration);

// 转发日志
        log.

info("转发到检测模块：{}/{}日志成功",successCount, logs.size());

// 写入日志
        log.

info("批量写入已完成：总计={}, 成功={}, 失败={}",
     logs.size(),totalSuccess,logs.

size() -totalSuccess);
```

---

## 配置示例

### 完整配置

```yaml
server:
  port: 10250

spring:
  application:
    name: logx-engine-processor

  # Kafka配置
  kafka:
    bootstrap-servers: localhost:29092
    consumer:
      group-id: logx-processor-group
      auto-offset-reset: latest
      enable-auto-commit: false
      max-poll-records: 500
      fetch-min-size: 1024
      fetch-max-wait: 500
      concurrency: 3

  # Elasticsearch配置
  data:
    elasticsearch:
      uris: http://localhost:9200
      username: elastic
      password: 8rc3Jl1jlAK3uVZZyhF4
      connection-timeout: 10000
      socket-timeout: 30000

# LogX业务配置
logx:
  # Kafka Topic
  kafka:
    topic:
      log-ingestion: logx-logs
      log-processing: logx-logs-processing
      dead-letter: logx-logs-dlq

  # 消费者配置
  consumer:
    max-retries: 3
    retry-backoff-ms: 1000

  # ES批量配置
  es:
    bulk:
      max-size: 500

# 日志配置
logging:
  level:
    com.domidodo.logx: DEBUG
  file:
    name: logs/processor.log
```

---

## 故障排查

### 1. Kafka消费堆积

**现象**: Kafka lag 持续增长

**排查步骤**:

```bash
# 1. 检查消费者状态
docker exec logx-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group logx-processor-group

# 2. 查看日志
tail -f logs/processor.log | grep "已处理"

# 3. 检查ES性能
curl http://localhost:9200/_cluster/health?pretty
```

**解决方案**:

```yaml
# 增加并发消费者
spring:
  kafka:
    consumer:
      concurrency: 5  # 从3增加到5
      max-poll-records: 1000  # 增加批量
```

---

### 2. ES写入失败

**现象**: 日志中大量 "写入ES失败"

**排查步骤**:

```bash
# 1. 检查ES健康
curl http://localhost:9200/_cluster/health

# 2. 检查索引
curl http://localhost:9200/_cat/indices?v | grep logx-logs

# 3. 查看ES日志
docker logs logx-es
```

**解决方案**:

```yaml
# 1. 增加ES堆内存
ES_JAVA_OPTS: "-Xms1g -Xmx1g"

# 2. 减小批量大小
logx:
  es:
    bulk:
      max-size: 200  # 从500减小到200
```

---

### 3. 内存溢出

**现象**: `OutOfMemoryError`

**排查步骤**:

```bash
# 1. 查看堆内存
jmap -heap <pid>

# 2. 生成堆转储
jmap -dump:format=b,file=heap.bin <pid>

# 3. 分析内存
jhat heap.bin
```

**解决方案**:

```bash
# 增加JVM内存
java -Xms2g -Xmx4g -jar processor.jar

# 或设置环境变量
export JAVA_OPTS="-Xms2g -Xmx4g"
```

---

## 最佳实践

### 1. 性能调优

```yaml
# 高吞吐量配置
spring:
  kafka:
    consumer:
      concurrency: 5
      max-poll-records: 1000

logx:
  es:
    bulk:
      max-size: 1000
      concurrent-requests: 4
```

### 2. 可靠性配置

```yaml
# 高可靠性配置
spring:
  kafka:
    consumer:
      enable-auto-commit: false  # 手动提交

logx:
  consumer:
    max-retries: 5              # 增加重试
    retry-backoff-ms: 2000      # 增加间隔
```

### 3. 资源规划

| 吞吐量          | 并发数 | 批量大小 | 内存   | CPU |
|--------------|-----|------|------|-----|
| <1000/s      | 2   | 500  | 1GB  | 1核  |
| 1000-5000/s  | 3   | 500  | 2GB  | 2核  |
| 5000-10000/s | 5   | 1000 | 4GB  | 4核  |
| >10000/s     | 10+ | 1000 | 8GB+ | 8核+ |

---

## 下一步

- 查看 [Storage模块文档](./LogX-Storage-Guide.md) 了解索引生命周期管理
- 查看 [Detection模块文档](./LogX-Detection-Guide.md) 了解异常检测规则
- 查看 [监控文档](./LogX-Configuration-Guide.md) 配置Prometheus和Grafana
