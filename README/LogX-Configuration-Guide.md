# LogX 配置详解与部署完整指南

## 📑 目录

- [配置文件完整示例](#配置文件完整示例)
- [中间件配置详解](#中间件配置详解)
- [Elasticsearch 索引设计](#elasticsearch-索引设计)
- [gRPC 协议说明](#grpc-协议说明)
- [一键部署脚本](#一键部署脚本)
- [监控与运维](#监控与运维)

---

## 配置文件完整示例

### 1. 单体应用配置 (logx-standalone/application.yml)

```yaml
server:
  port: 8080

spring:
  application:
    name: logx-standalone

  # ==================== 数据源配置 ====================
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3307/logx?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: root123
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20
      max-wait: 60000
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000
      validation-query: SELECT 1
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false

  # ==================== Redis 配置 ====================
  data:
    redis:
      host: localhost
      port: 6379
      password: redis123
      database: 0
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2
          max-wait: -1ms
      timeout: 5000ms

    # ==================== Elasticsearch 配置 ====================
    elasticsearch:
      uris: http://localhost:9200
      username: elastic
      password: 8rc3Jl1jlAK3uVZZyhF4
      connection-timeout: 10000
      socket-timeout: 30000

  # ==================== Kafka 配置 ====================
  kafka:
    bootstrap-servers: localhost:29092
    
    # 生产者配置
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: 1                        # 1=leader确认, all=所有副本确认
      retries: 3                     # 重试次数
      batch-size: 16384              # 批量大小 (16KB)
      buffer-memory: 33554432        # 缓冲内存 (32MB)
      compression-type: lz4          # 压缩算法: none, gzip, snappy, lz4, zstd
      linger-ms: 10                  # 批量发送延迟
    
    # 消费者配置
    consumer:
      group-id: logx-consumer-group
      auto-offset-reset: latest      # earliest=从头开始, latest=从最新
      enable-auto-commit: false      # 手动提交offset
      max-poll-records: 500          # 单次最多拉取500条
      fetch-min-size: 1024           # 最小拉取1KB
      fetch-max-wait: 500            # 最大等待500ms
      concurrency: 3                 # 并发消费者数量

# ==================== MyBatis Plus 配置 ====================
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.domidodo.logx.*.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

# ==================== MinIO 配置 ====================
minio:
  endpoint: http://localhost:9000
  access-key: admin
  secret-key: admin123
  bucket-name: logx-archive
  region: us-east-1

# ==================== LogX 业务配置 ====================
logx:
  # ---------- 存储配置 ----------
  storage:
    # 索引配置
    index:
      prefix: logx-logs              # 索引前缀
      shards: 5                      # 主分片数
      replicas: 1                    # 副本数
      refresh-interval: 5s           # 刷新间隔
    
    # 生命周期配置
    lifecycle:
      hot-data-days: 7               # 热数据保留天数 (高性能SSD)
      warm-data-days: 30             # 温数据保留天数 (普通磁盘)
      cold-data-days: 90             # 冷数据保留天数 (归档存储)
      cleanup-enabled: true          # 是否启用自动清理
      cleanup-cron: "0 0 2 * * ?"    # 清理任务cron表达式 (每天凌晨2点)
      archive-enabled: true          # 是否启用归档
      archive-cron: "0 0 3 * * ?"    # 归档任务cron表达式 (每天凌晨3点)
    
    # 压缩配置
    compression:
      enabled: true                  # 是否启用压缩
      algorithm: gzip                # 压缩算法: gzip, lz4
      level: 6                       # 压缩级别 (1-9, 数字越大压缩率越高但速度越慢)
    
    # 批量操作配置
    bulk:
      size: 1000                     # 批量大小
      flush-interval: 5m             # 刷新间隔
      concurrent-requests: 2         # 并发请求数

  # ---------- 限流配置 ----------
  rate-limit:
    enabled: true
    global-qps: 10000                # 全局每秒请求数
    tenant-qps: 1000                 # 租户每秒请求数
    system-qpm: 5000                 # 系统每分钟请求数

  # ---------- Kafka Topic 配置 ----------
  kafka:
    topics:
      logs: logx-logs                # 日志主题
      alerts: logx-alerts            # 告警主题
    partitions: 3                    # 分区数
    replication-factor: 1            # 副本因子

# ==================== 日志配置 ====================
logging:
  level:
    root: INFO
    com.domidodo.logx: DEBUG
    org.springframework.kafka: WARN
    org.elasticsearch: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n"
  file:
    name: logs/logx-standalone.log
    max-size: 100MB
    max-history: 30

# ==================== 接口文档配置 ====================
knife4j:
  enable: true
  setting:
    language: zh_CN
    swagger-model-name: 实体类列表
```

---

### 2. 网关服务配置 (logx-gateway-http/application.yml)

```yaml
server:
  port: 10240

spring:
  application:
    name: logx-gateway-http

  # 数据源 (仅用于验证API Key)
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3307/logx?useUnicode=true&characterEncoding=utf8
    username: root
    password: root123

  # Redis (用于限流)
  data:
    redis:
      host: localhost
      port: 6379
      password: redis123

  # Kafka (发送日志)
  kafka:
    bootstrap-servers: localhost:29092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: 1
      retries: 3

# 限流配置
logx:
  rate-limit:
    enabled: true
    global-qps: 10000
    tenant-qps: 1000

logging:
  level:
    com.domidodo.logx: DEBUG
  file:
    name: logs/gateway-http.log
```

---

### 3. 日志处理器配置 (logx-engine-processor/application.yml)

```yaml
server:
  port: 10250

spring:
  application:
    name: logx-engine-processor

  # Kafka消费
  kafka:
    bootstrap-servers: localhost:29092
    consumer:
      group-id: logx-processor-group
      auto-offset-reset: earliest
      enable-auto-commit: false
      max-poll-records: 500
      concurrency: 3

  # Elasticsearch写入
  data:
    elasticsearch:
      uris: http://localhost:9200
      username: elastic
      password: 8rc3Jl1jlAK3uVZZyhF4

# 批量写入配置
logx:
  storage:
    bulk:
      size: 1000
      flush-interval: 5m
      concurrent-requests: 2

logging:
  level:
    com.domidodo.logx: DEBUG
  file:
    name: logs/processor.log
```

---

### 4. 管理控制台配置 (logx-console-api/application.yml)

```yaml
server:
  port: 8083

spring:
  application:
    name: logx-console-api

  # 数据源
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3307/logx?useUnicode=true&characterEncoding=utf8
    username: root
    password: root123

  # Elasticsearch查询
  
elasticsearch:
  uris: http://localhost:9200
  username: elastic
  password: 8rc3Jl1jlAK3uVZZyhF4

  # Redis缓存
  data:
    redis:
      host: localhost
      port: 6379
      password: redis123

# API文档
knife4j:
  enable: true

logging:
  level:
    com.domidodo.logx: DEBUG
  file:
    name: logs/console-api.log
```

---

## 中间件配置详解

### 1. MySQL 数据库配置

**用途**: 存储租户、系统、规则等元数据

**连接信息**:
```
Host: localhost
Port: 3307 (容器映射，避免冲突)
Database: logx
User: root
Password: root123
```

**初始化脚本**: `scripts/init.sql`

**数据表**:
- `sys_tenant` - 租户表
- `sys_system` - 系统表
- `log_exception_rule` - 异常规则表
- `log_notification_config` - 通知配置表
- `log_alert_record` - 告警记录表

---

### 2. Elasticsearch 配置详解

#### 连接配置

```yaml
spring:
  data:
    elasticsearch:
      uris: http://localhost:9200
      username: elastic
      password: 8rc3Jl1jlAK3uVZZyhF4  # 首次启动自动生成
      connection-timeout: 10000
      socket-timeout: 30000
```

#### 索引命名规则

```
格式: {prefix}-{tenantId}-{systemId}-{date}
示例: logx-logs-company_a-erp_system-2024.12.27
```

#### 分片与副本策略

| 环境 | 主分片 | 副本数 | 说明 |
|------|--------|--------|------|
| 开发 | 1 | 0 | 单节点，无副本 |
| 测试 | 3 | 1 | 中等数据量 |
| 生产 | 5 | 1-2 | 大数据量，高可用 |

#### 生命周期管理

```
热数据 (Hot)   → 7天  → 高性能SSD，频繁读写
  ↓
温数据 (Warm)  → 30天 → 普通磁盘，只读查询
  ↓
冷数据 (Cold)  → 90天 → 归档存储(MinIO)，极少访问
  ↓
删除 (Delete)  → 过期后自动清理
```

---

### 3. Kafka 配置详解

#### Topic 规划

| Topic | 分区数 | 副本数 | 用途 |
|-------|--------|--------|------|
| logx-logs | 3 | 1 | 日志数据流 |
| logx-alerts | 1 | 1 | 告警通知 |

#### 生产者优化

```yaml
spring:
  kafka:
    producer:
      acks: 1                    # 权衡性能与可靠性
      batch-size: 16384          # 16KB批量
      linger-ms: 10              # 10ms延迟批量发送
      compression-type: lz4      # LZ4压缩 (速度快)
```

**性能对比**:
- `acks=0`: 不等待确认 (最快，可能丢失)
- `acks=1`: Leader确认 (均衡，推荐)
- `acks=all`: 所有副本确认 (最慢，最可靠)

#### 消费者优化

```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 500      # 单次拉取500条
      fetch-min-size: 1024       # 最小1KB才返回
      concurrency: 3             # 3个并发消费者
```

---

### 4. Redis 配置详解

#### 用途说明

| 用途 | Key 前缀 | TTL | 说明 |
|------|----------|-----|------|
| 限流 | `rate_limit:` | 1s-1m | 滑动窗口计数 |
| 缓存 | `cache:` | 1h | 热点数据缓存 |
| 分布式锁 | `lock:` | 30s | 防止并发冲突 |

#### 连接池配置

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 8          # 最大连接数
          max-idle: 8            # 最大空闲连接
          min-idle: 2            # 最小空闲连接
          max-wait: -1ms         # 无限等待
```

---

### 5. MinIO 配置详解

#### 访问信息

```
Console: http://localhost:9001
Access Key: admin
Secret Key: admin123
```

#### Bucket 设置

```yaml
minio:
  bucket-name: logx-archive
  region: us-east-1
```

#### 自动创建 Bucket

在 `MinioConfig` 中添加初始化逻辑：

```java
@PostConstruct
public void initBucket() throws Exception {
    boolean exists = minioClient.bucketExists(
        BucketExistsArgs.builder().bucket(bucketName).build()
    );
    
    if (!exists) {
        minioClient.makeBucket(
            MakeBucketArgs.builder().bucket(bucketName).build()
        );
        log.info("创建Bucket: {}", bucketName);
    }
}
```

---

## Elasticsearch 索引设计

### 1. 索引模板

索引模板在应用启动时自动创建 (`EsTemplateManager.java`)：

```json
{
  "index_patterns": ["logx-logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 5,
      "number_of_replicas": 1,
      "refresh_interval": "5s",
      "codec": "best_compression"
    },
    "mappings": {
      "properties": {
        "traceId": { "type": "keyword" },
        "spanId": { "type": "keyword" },
        "tenantId": { "type": "keyword" },
        "systemId": { "type": "keyword" },
        "timestamp": { "type": "date" },
        "level": { "type": "keyword" },
        "message": {
          "type": "text",
          "analyzer": "ik_max_word",
          "fields": {
            "keyword": { "type": "keyword", "ignore_above": 256 }
          }
        },
        "exception": { "type": "text" },
        "userId": { "type": "keyword" },
        "userName": { "type": "keyword" },
        "className": { "type": "keyword" },
        "methodName": { "type": "keyword" },
        "lineNumber": { "type": "integer" },
        "requestUrl": { "type": "keyword" },
        "requestMethod": { "type": "keyword" },
        "responseTime": { "type": "long" },
        "ip": { "type": "ip" },
        "tags": { "type": "keyword" },
        "extra": { "type": "object", "enabled": false }
      }
    }
  }
}
```

### 2. 字段说明

| 字段 | 类型 | 索引 | 说明 |
|------|------|------|------|
| traceId | keyword | ✅ | 全链路追踪ID |
| timestamp | date | ✅ | 日志时间戳 (核心查询字段) |
| level | keyword | ✅ | 日志级别 (常用过滤) |
| message | text | ✅ | 日志内容 (全文检索) |
| exception | text | ✅ | 异常堆栈 |
| ip | ip | ✅ | IP地址 (支持范围查询) |
| extra | object | ❌ | 扩展字段 (仅存储不索引) |

### 3. 查询示例

#### 按时间范围查询

```java
SearchRequest request = SearchRequest.of(s -> s
    .index("logx-logs-*")
    .query(q -> q
        .bool(b -> b
            .filter(f -> f.range(r -> r
                .field("timestamp")
                .gte(JsonData.of(startTime))
                .lte(JsonData.of(endTime))
            ))
        )
    )
    .sort(sort -> sort.field(f -> f.field("timestamp").order(SortOrder.Desc)))
);
```

#### 全文检索

```java
SearchRequest request = SearchRequest.of(s -> s
    .index("logx-logs-*")
    .query(q -> q
        .match(m -> m
            .field("message")
            .query(keyword)
        )
    )
);
```

#### 聚合统计

```java
SearchRequest request = SearchRequest.of(s -> s
    .index("logx-logs-*")
    .size(0)
    .aggregations("level_count", a -> a
        .terms(t -> t.field("level"))
    )
);
```

---

## gRPC 协议说明

### 1. Protocol Buffers 定义

#### log_service.proto

```protobuf
syntax = "proto3";
import "google/protobuf/struct.proto";

package logx;

service LogService {
  // 批量发送日志
  rpc SendLogs(LogBatchRequest) returns (LogBatchResponse);
  
  // 流式发送日志
  rpc StreamLogs(stream LogEntry) returns (LogBatchResponse);
}

message LogBatchRequest {
  string tenant_id = 1;
  string system_id = 2;
  string api_key = 3;
  repeated LogEntry logs = 4;
}

message LogEntry {
  string trace_id = 1;
  string tenant_id = 3;
  string system_id = 4;
  int64 timestamp = 5;
  string level = 6;
  string message = 12;
  google.protobuf.Struct extra = 25;  // 扩展字段
  // ... 其他字段省略
}
```

### 2. 使用 gRPC 的优势

| 特性 | HTTP/JSON | gRPC/Protobuf |
|------|-----------|---------------|
| 性能 | 较慢 | 快 2-5 倍 |
| 数据大小 | 较大 | 小 30-50% |
| 类型安全 | ❌ | ✅ |
| 流式传输 | 有限 | 原生支持 |
| 浏览器支持 | ✅ | 需要 gRPC-Web |

### 3. SDK 使用 gRPC

#### 配置

```yaml
logx:
  mode: grpc
  gateway:
    grpc-host: localhost
    grpc-port: 10241
```

#### 代码示例

```java
LogXClient client = LogXClient.builder()
    .tenantId("company_a")
    .systemId("erp_system")
    .apiKey("sk_test_key_001")
    .grpcEndpoint("localhost", 10241)
    .build();

client.info("测试gRPC日志");
```

### 4. 性能对比测试

测试条件: 10万条日志，每条500字节

| 模式 | 耗时 | 吞吐量 | 网络流量 |
|------|------|--------|----------|
| HTTP | 15.2s | 6,578/s | 48MB |
| gRPC | 6.8s | 14,705/s | 32MB |

**结论**: gRPC 速度提升 2.2倍，流量减少 33%

---

## 一键部署脚本

### 1. 完整启动脚本

创建 `scripts/start-all.sh`:

```bash
#!/bin/bash

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== LogX 一键部署脚本 ===${NC}"

# 1. 检查环境
echo -e "${YELLOW}[1/6] 检查环境...${NC}"

# 检查Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker未安装${NC}"
    exit 1
fi

# 检查Docker Compose
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}❌ Docker Compose未安装${NC}"
    exit 1
fi

# 检查JDK
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ JDK未安装${NC}"
    exit 1
else
    java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$java_version" -lt 17 ]; then
        echo -e "${RED}❌ JDK版本过低,需要JDK 17+${NC}"
        exit 1
    fi
fi

# 检查Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven未安装${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 环境检查通过${NC}"

# 2. 启动中间件
echo -e "${YELLOW}[2/6] 启动中间件 (MySQL, Redis, ES, Kafka, MinIO)...${NC}"
docker-compose up -d

echo "等待中间件就绪..."
sleep 30

# 检查中间件状态
if ! docker ps | grep -q logx-mysql; then
    echo -e "${RED}❌ MySQL启动失败${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 中间件启动成功${NC}"

# 3. 初始化数据库
echo -e "${YELLOW}[3/6] 初始化数据库...${NC}"
docker exec -i logx-mysql mysql -uroot -proot123 < scripts/init.sql
echo -e "${GREEN}✅ 数据库初始化完成${NC}"

# 4. 编译项目
echo -e "${YELLOW}[4/6] 编译项目...${NC}"
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 编译失败${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 编译完成${NC}"

# 5. 创建日志目录
echo -e "${YELLOW}[5/6] 创建日志目录...${NC}"
mkdir -p logs

# 6. 启动服务
echo -e "${YELLOW}[6/6] 启动LogX服务...${NC}"

# 选择部署模式
read -p "选择部署模式 [1=单体, 2=微服务]: " mode

if [ "$mode" == "1" ]; then
    # 单体模式
    echo "启动单体应用..."
    nohup java -jar logx-standalone/target/logx-standalone-0.0.1-SNAPSHOT.jar \
        > logs/standalone.log 2>&1 &
    echo $! > logs/standalone.pid
    echo -e "${GREEN}✅ 单体应用启动中...${NC}"
    
else
    # 微服务模式
    echo "启动微服务..."
    
    # HTTP网关
    nohup java -jar logx-gateway/logx-gateway-http/target/logx-gateway-http-0.0.1-SNAPSHOT.jar \
        > logs/gateway-http.log 2>&1 &
    echo $! > logs/gateway-http.pid
    
    # 日志处理器
    nohup java -jar logx-engine/logx-engine-processor/target/logx-engine-processor-0.0.1-SNAPSHOT.jar \
        > logs/processor.log 2>&1 &
    echo $! > logs/processor.pid
    
    # 异常检测
    nohup java -jar logx-engine/logx-engine-detection/target/logx-engine-detection-0.0.1-SNAPSHOT.jar \
        > logs/detection.log 2>&1 &
    echo $! > logs/detection.pid
    
    # 存储管理
    nohup java -jar logx-engine/logx-engine-storage/target/logx-engine-storage-0.0.1-SNAPSHOT.jar \
        > logs/storage.log 2>&1 &
    echo $! > logs/storage.pid
    
    # 管理控制台
    nohup java -jar logx-console/logx-console-api/target/logx-console-api-0.0.1-SNAPSHOT.jar \
        > logs/console-api.log 2>&1 &
    echo $! > logs/console-api.pid
    
    echo -e "${GREEN}✅ 微服务启动中...${NC}"
fi

echo ""
echo -e "${GREEN}=== 部署完成! ===${NC}"
echo ""
echo "服务地址:"
echo "  - HTTP网关:    http://localhost:10240"
echo "  - 管理控制台:   http://localhost:8083"
echo "  - API文档:     http://localhost:8083/doc.html"
echo "  - Kibana:      http://localhost:5601"
echo "  - MinIO:       http://localhost:9001"
echo ""
echo "查看日志:"
echo "  tail -f logs/*.log"
echo ""
```

### 2. 停止脚本

创建 `scripts/stop-all.sh`:

```bash
#!/bin/bash

echo "停止LogX服务..."

# 读取PID并停止
for pidfile in logs/*.pid; do
    if [ -f "$pidfile" ]; then
        pid=$(cat "$pidfile")
        if ps -p $pid > /dev/null; then
            echo "停止进程 $pid..."
            kill $pid
        fi
        rm -f "$pidfile"
    fi
done

echo "停止中间件..."
docker-compose down

echo "✅ 全部停止完成"
```

### 3. 赋予执行权限

```bash
chmod +x scripts/start-all.sh
chmod +x scripts/stop-all.sh
```

---

## 监控与运维

### 1. 健康检查

#### 检查脚本

创建 `scripts/health-check.sh`:

```bash
#!/bin/bash

echo "=== LogX 健康检查 ==="

# 检查中间件
echo ""
echo "中间件状态:"
docker ps --filter "name=logx-" --format "table {{.Names}}\t{{.Status}}"

# 检查应用端口
echo ""
echo "应用端口:"
for port in 8080 10240 10250 8083; do
    if nc -z localhost $port 2>/dev/null; then
        echo "✅ 端口 $port: 正常"
    else
        echo "❌ 端口 $port: 异常"
    fi
done

# 检查ES健康
echo ""
echo "Elasticsearch:"
curl -s http://localhost:9200/_cluster/health | jq .

# 检查Kafka
echo ""
echo "Kafka Topics:"
docker exec logx-kafka kafka-topics.sh \
    --bootstrap-server localhost:9092 --list
```

### 2. 性能监控指标

#### 关键指标

| 指标类型 | 指标名称 | 告警阈值 | 说明 |
|---------|---------|---------|------|
| QPS | 日志写入QPS | >10000 | 网关吞吐量 |
| 延迟 | ES写入延迟 | >100ms | 写入性能 |
| 队列 | Kafka消息堆积 | >10000 | 消费能力 |
| 资源 | ES堆内存使用率 | >80% | 内存压力 |

#### Prometheus 集成

在 `application.yml` 添加:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### 3. 日志轮转

使用 logback 配置:

```xml
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/logx.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/logx.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <timeBasedFileNamingAndTriggeringPolicy 
                class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>100MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

---

## 常见问题排查

### 1. ES 连接失败

**现象**: `ElasticsearchException: Connection refused`

**排查步骤**:
```bash
# 1. 检查ES是否启动
docker ps | grep elasticsearch

# 2. 查看ES日志
docker logs logx-es

# 3. 测试连接
curl http://localhost:9200

# 4. 重置密码
docker exec -it logx-es elasticsearch-reset-password -u elastic
```

### 2. Kafka 消费堆积

**现象**: 日志延迟，`lag` 值很大

**解决方案**:
```yaml
# 增加消费者并发数
spring:
  kafka:
    consumer:
      concurrency: 5  # 从3增加到5
      max-poll-records: 1000  # 增加批量大小
```

### 3. 内存溢出

**现象**: `OutOfMemoryError: Java heap space`

**解决方案**:
```bash
# 增加JVM堆内存
java -Xms2g -Xmx4g -jar app.jar

# 或在启动脚本中设置
export JAVA_OPTS="-Xms2g -Xmx4g"
```

---

## 性能优化建议

### 1. Elasticsearch 优化

```yaml
logx:
  storage:
    index:
      shards: 3              # 减少分片数 (数据量<500GB)
      refresh-interval: 30s  # 增加刷新间隔 (降低写入压力)
    bulk:
      size: 5000             # 增加批量大小
      concurrent-requests: 4 # 增加并发写入
```

### 2. Kafka 优化

```yaml
spring:
  kafka:
    producer:
      batch-size: 32768      # 增加到32KB
      linger-ms: 20          # 延迟20ms批量发送
      compression-type: zstd # 使用zstd压缩 (压缩率更高)
```

### 3. 应用层优化

- 使用缓冲区减少网络请求
- 异步发送日志
- 合理设置连接池大小
- 启用日志压缩
