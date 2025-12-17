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

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                        应用系统                                │
│  (集成 LogX SDK - HTTP/gRPC)                                 │
└───────────────────┬──────────────────┬──────────────────────┘
                    │                  │
                    ▼                  ▼
        ┌──────────────────┐  ┌──────────────────┐
        │   HTTP Gateway   │  │   gRPC Gateway   │
        │   (接入层)        │  │   (接入层)        │
        └─────────┬────────┘  └─────────┬────────┘
                  │                     │
                  └──────────┬──────────┘
                             ▼
                    ┌─────────────────┐
                    │  Kafka (消息队列)│
                    └────────┬────────┘
                             ▼
                    ┌─────────────────┐
                    │  Log Processor   │
                    │  (日志处理器)     │
                    └────────┬────────┘
                             ▼
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
┌───────────────┐  ┌─────────────────┐  ┌──────────────┐
│ Elasticsearch │  │  Detection      │  │   Storage    │
│ (日志存储)     │  │  (异常检测)      │  │  (归档管理)   │
└───────────────┘  └────────┬────────┘  └──────────────┘
        ▲                   │
        │                   ▼
        │          ┌─────────────────┐
        │          │  Notification   │
        │          │  (告警通知)      │
        │          └─────────────────┘
        │
        ▼
┌───────────────────────────────────┐
│      Console API (管理控制台)       │
│  - 日志查询  - 统计分析             │
│  - 系统管理  - 规则配置             │
└───────────────────────────────────┘
```

## 📦 模块说明

| 模块 | 说明 | 技术栈 |
|------|------|--------|
| **logx-common** | 公共模块 | 基础工具类、DTO、常量 |
| **logx-infrastructure** | 基础设施 | ES、Kafka、Redis、MyBatis Plus |
| **logx-sdk** | SDK组件 | 日志客户端、Spring Boot Starter |
| **logx-gateway** | 接入网关 | HTTP/gRPC 日志接收 |
| **logx-engine-processor** | 日志处理 | 解析、标准化、脱敏 |
| **logx-engine-storage** | 存储管理 | 生命周期、归档、导出 |
| **logx-engine-detection** | 异常检测 | 规则引擎、告警触发 |
| **logx-console-api** | 管理控制台 | 查询、分析、配置 |
| **logx-standalone** | 单体应用 | 集成所有模块 |

## 🚀 快速开始

### 前置要求

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose
- 8GB+ RAM

### 1. 启动基础设施

```bash
# 启动 MySQL、Redis、Elasticsearch、Kafka、MinIO
docker-compose up -d

# 等待服务就绪（约1-2分钟）
docker-compose ps
```

### 2. 初始化数据库

```bash
# 执行初始化脚本
docker exec -i logx-mysql mysql -uroot -proot123 < scripts/init.sql
```

### 3. 编译项目

```bash
mvn clean package -DskipTests
```

### 4. 启动服务（微服务模式）

```bash
# 方式1: 分别启动各个服务
cd logx-gateway/logx-gateway-http
mvn spring-boot:run

cd logx-engine/logx-engine-processor
mvn spring-boot:run

cd logx-engine/logx-engine-detection
mvn spring-boot:run

cd logx-console/logx-console-api
mvn spring-boot:run
```

### 5. 启动服务（单体模式）

```bash
cd logx-standalone
mvn spring-boot:run
```

### 6. 访问控制台

- **API 文档**: http://localhost:8083/doc.html
- **Kibana**: http://localhost:5601
- **MinIO 控制台**: http://localhost:9001 (admin/admin123)

## 💻 SDK 使用示例

### Spring Boot 集成

#### 1. 添加依赖

```xml
<dependency>
    <groupId>com.domidodo</groupId>
    <artifactId>logx-sdk-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

#### 2. 配置文件

```yaml
logx:
  enabled: true
  tenant-id: 1
  system-id: 1001
  system-name: "我的应用"
  mode: http  # 或 grpc
  gateway:
    url: http://localhost:10240
  buffer:
    enabled: true
    size: 1000
    flush-interval: 5s
  aspect:
    enabled: true
    controller: true
    service: false
    slow-threshold: 3000
```

#### 3. 使用日志记录器

```java
import com.domidodo.logx.sdk.core.LogXLogger;

public class UserService {
    private static final LogXLogger logger = LogXLogger.getLogger(UserService.class);
    
    public User createUser(User user) {
        logger.info("创建用户: " + user.getName());
        
        try {
            // 业务逻辑
            return userRepository.save(user);
        } catch (Exception e) {
            logger.error("创建用户失败", e);
            throw e;
        }
    }
}
```

### 原生 Java 集成

```java
import com.domidodo.logx.sdk.core.LogXClient;

public class Application {
    public static void main(String[] args) {
        // 创建客户端
        LogXClient client = LogXClient.builder()
            .tenantId(1L)
            .systemId(1001L)
            .systemName("我的应用")
            .gatewayUrl("http://localhost:10240")
            .build();
        
        // 记录日志
        client.info("应用启动成功");
        
        try {
            // 业务逻辑
        } catch (Exception e) {
            client.error("发生异常", e);
        } finally {
            // 关闭客户端
            client.shutdown();
        }
    }
}
```

## 🔧 配置说明

### 核心配置项

```yaml
# 生命周期管理
logx.storage.lifecycle:
  hot-data-days: 7      # 热数据保留天数（高性能）
  warm-data-days: 30    # 温数据保留天数（只读）
  cold-data-days: 90    # 冷数据保留天数（归档）
  cleanup-enabled: true
  archive-enabled: true

# 限流配置
logx.rate-limit:
  enabled: true
  global.qps: 10000     # 全局每秒请求数
  tenant.qps: 1000      # 租户每秒请求数
  system.qpm: 5000      # 系统每分钟请求数
```

## 📊 核心功能

### 1. 日志查询

```bash
curl -X POST http://localhost:8083/api/logs/query \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "company_a",
    "systemId": "erp_system",
    "startTime": "2024-12-01 00:00:00",
    "endTime": "2024-12-17 23:59:59",
    "level": "ERROR",
    "keyword": "异常",
    "page": 1,
    "size": 20
  }'
```

### 2. 异常规则配置

```json
{
  "ruleName": "响应时间异常",
  "ruleType": "RESPONSE_TIME",
  "monitorTarget": "订单模块",
  "monitorMetric": "responseTime",
  "conditionOperator": ">",
  "conditionValue": "3000",
  "alertLevel": "WARNING"
}
```

### 3. 数据导出

```bash
# 导出索引数据
curl http://localhost:8085/api/export/index/logx-logs-company_a-erp_system-2024.12.16

# 批