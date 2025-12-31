# LogX - 企业级日志管理与分析平台

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue" alt="Java 17">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.2-green" alt="Spring Boot">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
</p>

## 📖 项目简介

LogX 是一个功能完善的企业级日志管理与分析平台，支持多租户、多系统架构，提供日志收集、存储、查询、分析、告警等全方位能力。

### ✨ 核心特性

- 🏢 **多租户架构** - 完善的租户隔离和数据安全
- 🔌 **多种接入方式** - 支持 HTTP/gRPC 双协议
- 📊 **实时分析** - 基于 Elasticsearch 的高性能日志检索
- 🔔 **智能告警** - 灵活的规则引擎和多渠道通知
- 💾 **生命周期管理** - 热-温-冷数据分层存储
- 📈 **可视化分析** - 丰富的统计图表和仪表盘
- 🛡️ **安全可靠** - 完善的认证、限流和防护机制
- 🚀 **高性能** - 批量处理、缓冲优化、异步消费

---

## 🏗️ 模块架构

### 模块依赖关系树

```
LogX (根项目)
│
├── logx-common (公共模块)
│   ├── logx-common-core         # 核心工具类、DTO、常量、枚举
│   ├── logx-common-api          # API 接口定义、VO
│   └── logx-common-grpc         # gRPC 协议定义 (Protocol Buffers)
│
├── logx-infrastructure          # 基础设施层
│   └── ES、Kafka、Redis、MyBatis Plus 配置
│
├── logx-sdk (客户端 SDK)
│   ├── logx-sdk-core            # 纯 Java SDK，最小依赖
│   └── logx-sdk-spring-boot-starter  # Spring Boot 自动配置
│
├── logx-gateway (接入网关)
│   ├── logx-gateway-http        # HTTP 协议接入 (REST API)
│   └── logx-gateway-grpc        # gRPC 协议接入 (高性能)
│
├── logx-engine (处理引擎)
│   ├── logx-engine-processor    # 日志解析、标准化、脱敏
│   ├── logx-engine-storage      # 生命周期管理、归档、导出
│   └── logx-engine-detection    # 异常检测、告警触发
│
├── logx-console (管理控制台)
│   └── logx-console-api         # 查询、分析、配置 API
│
└── logx-standalone              # 单体应用 (包含所有服务)
```

### 模块说明

| 模块 | 说明 | 主要依赖 | 部署方式 |
|------|------|---------|---------|
| **logx-common-core** | 公共核心类库 | Hutool, FastJSON2 | JAR 依赖 |
| **logx-common-api** | 接口定义 | logx-common-core | JAR 依赖 |
| **logx-common-grpc** | gRPC 协议 | gRPC, Protobuf | JAR 依赖 |
| **logx-infrastructure** | 基础设施配置 | ES, Kafka, Redis, MyBatis Plus | JAR 依赖 |
| **logx-sdk-core** | 纯 Java SDK | FastJSON2, SLF4J | JAR 依赖 |
| **logx-sdk-spring-boot-starter** | Spring Boot 集成 | sdk-core, AOP | JAR 依赖 |
| **logx-gateway-http** | HTTP 网关 | Spring Web, Kafka, Redis | 独立部署 |
| **logx-gateway-grpc** | gRPC 网关 | gRPC Server, Kafka | 独立部署 |
| **logx-engine-processor** | 日志处理器 | Kafka, Elasticsearch | 独立部署 |
| **logx-engine-storage** | 存储管理 | Elasticsearch, MinIO | 独立部署 |
| **logx-engine-detection** | 异常检测 | Elasticsearch, 通知服务 | 独立部署 |
| **logx-console-api** | 管理控制台 | Elasticsearch, MyBatis Plus | 独立部署 |
| **logx-standalone** | 单体应用 | 集成所有上述服务 | 独立部署 |

---

## 🔧 技术栈

### 核心框架

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.2.2 | 应用框架 |
| Spring Cloud | 2023.0.0 | 微服务组件 |
| MyBatis Plus | 3.5.7 | ORM 框架 |

### 中间件

| 技术 | 用途 | 是否必需 |
|------|------|---------|
| MySQL | 存储租户、系统、规则等配置信息 | ✅ 必需 |
| Elasticsearch | 日志数据存储与检索 | ✅ 必需 |
| Kafka | 日志消息队列 | ✅ 必需 |
| Redis | 缓存、限流、分布式锁 | ✅ 必需 |
| MinIO | 冷数据归档存储 | ⚠️ 可选 |

### 工具库

| 工具 | 版本 | 用途 |
|------|------|------|
| Hutool | 5.8.34 | Java 工具集 |
| FastJSON2 | 2.0.54 | JSON 处理 |
| Druid | 1.2.27 | 数据库连接池 |
| Redisson | 3.37.0 | Redis 客户端 |
| gRPC | 1.59.0 | RPC 框架 (可选) |
| Knife4j | 4.5.0 | API 文档 |
| EasyExcel | 4.0.3 | Excel 导入导出 |

---

## 🚀 快速开始

### 环境要求

#### 最低配置

| 项目 | 要求 |
|------|------|
| JDK | 17+ |
| Maven | 3.8+ |
| Docker | 20.10+ (用于运行中间件) |
| Docker Compose | 1.29+ |
| 内存 | 8GB+ |
| 磁盘 | 20GB+ (用于 ES 数据) |

#### 推荐配置

| 项目 | 推荐 |
|------|------|
| 内存 | 16GB+ |
| CPU | 4 核+ |
| 磁盘 | 100GB+ SSD |

### 部署方式选择

LogX 支持两种部署方式：

#### 1. **单体模式** (推荐用于开发/测试)
- 只需要启动 `logx-standalone` 一个应用
- 包含所有功能模块
- 配置简单，资源占用较少
- 适合：开发环境、小规模生产环境

#### 2. **微服务模式** (推荐用于生产)
- 独立部署各个服务
- 可独立扩展、升级
- 高可用、故障隔离
- 适合：大规模生产环境

---

## 📦 部署指南

### 方式一：单体模式部署 (推荐新手)

#### 1. 启动基础设施

```bash
# 克隆项目
git clone https://github.com/I-have-a/logx.git
cd LogX

# 启动 MySQL、Redis、Elasticsearch、Kafka
docker-compose up -d

# 等待服务就绪 (约 1-2 分钟)
docker-compose ps
```

#### 2. 初始化数据库

```bash
# 执行初始化脚本
docker exec -i logx-mysql mysql -uroot -proot123 < scripts/init.sql
```

#### 3. 编译打包

```bash
mvn clean package -DskipTests
```

#### 4. 启动应用

```bash
cd logx-standalone
java -jar target/logx-standalone-0.0.1-SNAPSHOT.jar

# 或使用 Maven 运行
mvn spring-boot:run
```

#### 5. 验证服务

```bash
# 检查健康状态
curl http://localhost:8083/actuator/health

# 访问 API 文档
# 浏览器打开: http://localhost:8080/doc.html
```

---

### 方式二：微服务模式部署

#### 1. 基础设施 (同单体模式)

```bash
docker-compose up -d
docker exec -i logx-mysql mysql -uroot -proot123 < scripts/init.sql
```

#### 2. 编译所有模块

```bash
mvn clean package -DskipTests
```

#### 3. 启动各个服务

**方式 A：使用 Maven (开发推荐)**

```bash
# 终端 1: 启动 HTTP 网关
cd logx-gateway/logx-gateway-http
mvn spring-boot:run

# 终端 2: 启动日志处理器
cd logx-engine/logx-engine-processor
mvn spring-boot:run

# 终端 3: 启动异常检测
cd logx-engine/logx-engine-detection
mvn spring-boot:run

# 终端 4: 启动存储管理
cd logx-engine/logx-engine-storage
mvn spring-boot:run

# 终端 5: 启动管理控制台
cd logx-console/logx-console-api
mvn spring-boot:run
```

**方式 B：使用 JAR (生产推荐)**

```bash
# 创建部署脚本 start-all.sh
cat > start-all.sh << 'EOF'
#!/bin/bash

# 网关
nohup java -jar logx-gateway/logx-gateway-http/target/logx-gateway-http-0.0.1-SNAPSHOT.jar > logs/gateway.log 2>&1 &

# 处理引擎
nohup java -jar logx-engine/logx-engine-processor/target/logx-engine-processor-0.0.1-SNAPSHOT.jar > logs/processor.log 2>&1 &
nohup java -jar logx-engine/logx-engine-detection/target/logx-engine-detection-0.0.1-SNAPSHOT.jar > logs/detection.log 2>&1 &
nohup java -jar logx-engine/logx-engine-storage/target/logx-engine-storage-0.0.1-SNAPSHOT.jar > logs/storage.log 2>&1 &

# 控制台
nohup java -jar logx-console/logx-console-api/target/logx-console-api-0.0.1-SNAPSHOT.jar > logs/console.log 2>&1 &

echo "All services started!"
EOF

chmod +x start-all.sh
./start-all.sh
```

#### 4. 服务端口说明

| 服务 | 端口    | 说明 |
|------|-------|------|
| logx-gateway-http | 10240 | HTTP 日志接入 |
| logx-gateway-grpc | 8082  | gRPC 日志接入 |
| logx-engine-processor | 8081  | 日志处理器 (内部服务) |
| logx-engine-storage | 8085  | 存储管理 (内部服务) |
| logx-engine-detection | 8084  | 异常检测 (内部服务) |
| logx-console-api | 8083  | 管理控制台 API |

---

## 💻 SDK 使用指南

### 集成方式对比

| 方式 | 适用场景 | 依赖 | 配置复杂度 |
|------|---------|------|-----------|
| Spring Boot Starter | Spring Boot 应用 | 自动配置 | ⭐ 简单 |
| 纯 Java SDK | 普通 Java 应用 | 最小依赖 | ⭐⭐ 中等 |

---

### 方式一：Spring Boot 集成 (推荐)

#### 1. 添加 Maven 依赖

```xml
<dependency>
    <groupId>com.domidodo</groupId>
    <artifactId>logx-sdk-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

**最小依赖树：**
```
logx-sdk-spring-boot-starter
├── logx-sdk-core
│   ├── fastjson2
│   ├── slf4j-api
│   └── logx-common-grpc (可选，启用 gRPC 时)
├── spring-boot-starter
├── spring-boot-starter-aop
└── spring-boot-starter-web (可选)
```

#### 2. 配置文件 (application.yml)

**最小配置：**

```yaml
logx:
  enabled: true
  tenant-id: 1                    # 租户 ID
  system-id: 1001                 # 系统 ID
  system-name: "我的应用"          # 系统名称
  gateway:
    url: http://localhost:10240   # 网关地址
```

**完整配置：**

```yaml
logx:
  enabled: true                   # 是否启用 (默认: true)
  tenant-id: 1                    # 租户 ID (必填)
  system-id: 1001                 # 系统 ID (必填)
  system-name: "我的应用"          # 系统名称 (必填)
  
  # 接入方式
  mode: http                      # http 或 grpc (默认: http)
  
  # 网关配置
  gateway:
    url: http://localhost:10240   # HTTP 网关地址
    # grpc-host: localhost        # gRPC 网关地址 (mode=grpc 时)
    # grpc-port: 10241            # gRPC 端口
  
  # 缓冲配置
  buffer:
    enabled: true                 # 是否启用缓冲 (默认: true)
    size: 1000                    # 缓冲区大小 (默认: 1000)
    flush-interval: 5s            # 刷新间隔 (默认: 5s)
  
  # AOP 切面配置
  aspect:
    enabled: true                 # 是否启用切面 (默认: true)
    controller: true              # 是否拦截 Controller (默认: true)
    service: false                # 是否拦截 Service (默认: false)
    repository: false             # 是否拦截 Repository (默认: false)
    slow-threshold: 3000          # 慢请求阈值 (ms，默认: 3000)
  
  # 异步配置
  async:
    enabled: true                 # 是否异步发送 (默认: true)
    core-pool-size: 2             # 核心线程数 (默认: 2)
    max-pool-size: 5              # 最大线程数 (默认: 5)
    queue-capacity: 500           # 队列容量 (默认: 500)
```

#### 3. 使用示例

**方式 A：使用 LogXLogger (推荐)**

```java
import com.domidodo.logx.sdk.core.LogXLogger;

@Service
public class UserService {
    private static final LogXLogger logger = LogXLogger.getLogger(UserService.class);
    
    public User createUser(User user) {
        // 记录信息日志
        logger.info("创建用户: {}", user.getName());
        
        try {
            User saved = userRepository.save(user);
            logger.info("用户创建成功, ID: {}", saved.getId());
            return saved;
        } catch (Exception e) {
            // 记录错误日志 (自动捕获堆栈)
            logger.error("创建用户失败", e);
            throw e;
        }
    }
    
    public void batchProcess(List<User> users) {
        long start = System.currentTimeMillis();
        logger.info("开始批量处理, 数量: {}", users.size());
        
        for (User user : users) {
            processUser(user);
        }
        
        long cost = System.currentTimeMillis() - start;
        logger.info("批量处理完成, 耗时: {}ms", cost);
    }
}
```

**方式 B：自动切面拦截**

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    // 自动记录请求日志 (开启 aspect.controller=true)
    
    @PostMapping
    public Result<User> create(@RequestBody User user) {
        // 进入方法时自动记录: 请求参数、IP、User-Agent
        // 离开方法时自动记录: 响应结果、耗时
        return Result.success(userService.createUser(user));
    }
    
    // 慢请求自动告警 (超过 aspect.slow-threshold)
    @GetMapping("/report")
    public Result<Report> generateReport() {
        // 如果耗时超过 3000ms，自动记录 WARN 日志
        return Result.success(reportService.generate());
    }
}
```

---

### 方式二：纯 Java 集成

#### 1. 添加依赖

```xml
<dependency>
    <groupId>com.domidodo</groupId>
    <artifactId>logx-sdk-core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

**依赖树：**
```
logx-sdk-core
├── fastjson2 (2.0.54)
└── slf4j-api (2.0.x)
```

#### 2. 初始化客户端

```java
import com.domidodo.logx.sdk.core.LogXClient;
import com.domidodo.logx.sdk.core.config.LogXConfig;

public class Application {
    private static LogXClient logXClient;
    
    public static void main(String[] args) {
        // 方式 A: 使用 Builder
        logXClient = LogXClient.builder()
            .tenantId(1L)
            .systemId(1001L)
            .systemName("我的应用")
            .gatewayUrl("http://localhost:10240")
            .bufferEnabled(true)
            .bufferSize(1000)
            .build();
        
        // 方式 B: 使用 Config 对象
        LogXConfig config = new LogXConfig();
        config.setTenantId(1L);
        config.setSystemId(1001L);
        config.setSystemName("我的应用");
        config.setGatewayUrl("http://localhost:10240");
        
        logXClient = new LogXClient(config);
        
        // 使用日志
        logXClient.info("应用启动成功");
        
        // 业务逻辑...
        
        // 关闭客户端 (应用退出时)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logXClient.shutdown();
        }));
    }
}
```

#### 3. 使用示例

```java
public class OrderService {
    public void processOrder(Order order) {
        logXClient.info("处理订单: " + order.getId());
        
        try {
            // 业务逻辑
            paymentService.pay(order);
            logXClient.info("订单支付成功: " + order.getId());
            
        } catch (PaymentException e) {
            // 记录业务异常
            logXClient.warn("订单支付失败: " + order.getId(), e);
            
        } catch (Exception e) {
            // 记录系统异常
            logXClient.error("订单处理异常: " + order.getId(), e);
            throw e;
        }
    }
}
```

---

## 🔐 最低配置要求

### Docker Compose 最小配置

以下是运行 LogX 所需的最小 docker-compose.yml：

```yaml
version: '3.8'

services:
  # MySQL - 存储配置信息
  mysql:
    image: mysql:8.0
    container_name: logx-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: logx
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql

  # Redis - 缓存和限流
  redis:
    image: redis:7-alpine
    container_name: logx-redis
    ports:
      - "6379:6379"

  # Elasticsearch - 日志存储
  elasticsearch:
    image: elasticsearch:8.11.0
    container_name: logx-es
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"  # 最小 512MB
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data

  # Kafka - 消息队列
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: logx-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: logx-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

volumes:
  mysql-data:
  es-data:
```

### 应用最小配置 (application.yml)

```yaml
spring:
  # 数据源
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/logx?useUnicode=true&characterEncoding=utf8
    username: root
    password: root123
  
  # Redis
  data:
    redis:
      host: localhost
      port: 6379
    
    # Elasticsearch
    elasticsearch:
      uris: http://localhost:9200
  
  # Kafka
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: logx-processor
      auto-offset-reset: earliest
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

# LogX 配置
logx:
  # 租户默认配置
  tenant:
    default-id: 1
  
  # 限流配置
  rate-limit:
    enabled: true
    global-qps: 10000
    tenant-qps: 1000
  
  # 生命周期
  storage:
    lifecycle:
      hot-data-days: 7
      warm-data-days: 30
      cold-data-days: 90
      cleanup-enabled: true
```

---

## 📊 管理控制台使用

### 访问地址

- **API 文档**: http://localhost:8083/doc.html
- **健康检查**: http://localhost:8083/actuator/health

### 核心 API

#### 1. 日志查询

```bash
curl -X POST http://localhost:8083/api/logs/query \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": 1,
    "systemId": 1001,
    "startTime": "2024-12-01 00:00:00",
    "endTime": "2024-12-27 23:59:59",
    "level": "ERROR",
    "keyword": "异常",
    "page": 1,
    "size": 20
  }'
```

#### 2. 统计分析

```bash
# 按级别统计
curl http://localhost:8083/api/logs/stats/level?tenantId=1&systemId=1001

# 按时间段统计
curl http://localhost:8083/api/logs/stats/timeline?tenantId=1&systemId=1001&interval=1h
```

#### 3. 异常规则管理

```bash
# 创建规则
curl -X POST http://localhost:8083/api/rules \
  -H "Content-Type: application/json" \
  -d '{
    "ruleName": "高错误率告警",
    "ruleType": "ERROR_RATE",
    "tenantId": 1,
    "systemId": 1001,
    "threshold": 100,
    "timeWindow": 300,
    "alertLevel": "CRITICAL"
  }'
```

---

## 🛠️ 常见问题

### Q1: 如何切换 HTTP/gRPC 模式?

**A:** 修改 SDK 配置的 `mode` 参数：

```yaml
logx:
  mode: grpc  # 改为 grpc
  gateway:
    grpc-host: localhost
    grpc-port: 10241
```

### Q2: 如何调整日志保留时间?

**A:** 修改服务端配置：

```yaml
logx:
  storage:
    lifecycle:
      hot-data-days: 3    # 热数据保留 3 天
      warm-data-days: 15  # 温数据保留 15 天
      cold-data-days: 60  # 冷数据保留 60 天
```

### Q3: 如何启用 MinIO 归档?

**A:** 添加 MinIO 配置：

```yaml
minio:
  endpoint: http://localhost:9000
  access-key: admin
  secret-key: admin123
  bucket: logx-archive

logx:
  storage:
    archive:
      enabled: true
      provider: minio
```

### Q4: 如何监控 SDK 性能?

**A:** SDK 提供了指标接口：

```java
import com.domidodo.logx.sdk.core.metrics.LogXMetrics;

// 获取指标
LogXMetrics metrics = logXClient.getMetrics();
System.out.println("发送成功: " + metrics.getSuccessCount());
System.out.println("发送失败: " + metrics.getFailureCount());
System.out.println("平均耗时: " + metrics.getAvgLatency() + "ms");
```

---

## 📄 许可证

MIT License

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request!
