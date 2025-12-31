# LogX 项目结构说明

## 📂 完整目录结构

```
LogX/
├── docker-compose.yml              # Docker 编排文件
├── pom.xml                         # Maven 根项目配置
├── README.md                       # 项目说明文档
│
├── scripts/                        # 脚本文件目录
│   ├── init.sql                   # 数据库初始化脚本
│   ├── start-all.sh               # 启动脚本
│   └── stop-all.sh                # 停止脚本
│
├── logx-common/                    # 公共模块 (父项目)
│   ├── pom.xml
│   │
│   ├── logx-common-core/          # 核心公共类
│   │   ├── pom.xml
│   │   └── src/main/java/com/domidodo/logx/common/
│   │       ├── constant/          # 常量定义
│   │       │   └── SystemConstant.java
│   │       ├── context/           # 上下文管理
│   │       │   └── TenantContext.java
│   │       ├── enums/             # 枚举类
│   │       │   ├── AlertLevelEnum.java
│   │       │   └── LogLevelEnum.java
│   │       ├── exception/         # 异常处理
│   │       │   ├── BusinessException.java
│   │       │   └── GlobalExceptionHandler.java
│   │       ├── result/            # 统一响应结果
│   │       │   ├── Result.java
│   │       │   └── PageResult.java
│   │       ├── util/              # 工具类
│   │       │   ├── JsonUtil.java
│   │       │   └── SnowflakeIdGenerator.java
│   │       └── validator/         # 参数校验
│   │           └── InputValidator.java
│   │
│   ├── logx-common-api/           # API 接口定义
│   │   ├── pom.xml
│   │   └── src/main/java/com/domidodo/logx/common/dto/
│   │       ├── LogDTO.java
│   │       ├── QueryDTO.java
│   │       └── SystemDTO.java
│   │
│   └── logx-common-grpc/          # gRPC 协议定义
│       ├── pom.xml
│       └── src/main/proto/
│           ├── log_service.proto       # 日志服务协议
│           └── query_service.proto     # 查询服务协议
│
├── logx-infrastructure/            # 基础设施配置
│   ├── pom.xml
│   └── src/main/java/com/domidodo/logx/infrastructure/
│       ├── config/                # 配置类
│       │   ├── ElasticsearchConfig.java
│       │   ├── KafkaConsumerConfig.java
│       │   ├── KafkaProducerConfig.java
│       │   ├── MyBatisPlusConfig.java
│       │   ├── RedisConfig.java
│       │   └── WebMvcConfig.java
│       ├── handler/               # 处理器
│       │   └── MyTenantLineHandler.java
│       ├── interceptor/           # 拦截器
│       │   └── TenantInterceptor.java
│       ├── security/              # 安全组件
│       │   └── ApiKeyValidator.java
│       └── util/                  # 工具类
│           └── RedisRateLimiter.java
│
├── logx-sdk/                       # SDK 模块 (父项目)
│   ├── pom.xml
│   │
│   ├── logx-sdk-core/             # 核心 SDK (纯Java)
│   │   ├── pom.xml
│   │   └── src/main/java/com/domidodo/logx/sdk/core/
│   │       ├── LogXClient.java         # 主客户端
│   │       ├── LogXLogger.java         # 日志记录器
│   │       ├── buffer/                 # 缓冲管理
│   │       │   └── LogBuffer.java
│   │       ├── config/                 # 配置类
│   │       │   └── LogXConfig.java
│   │       ├── model/                  # 数据模型
│   │       │   └── LogEntry.java
│   │       └── sender/                 # 日志发送器
│   │           ├── LogSender.java          # 发送器接口
│   │           ├── HttpLogSender.java      # HTTP 发送实现
│   │           └── GrpcLogSender.java      # gRPC 发送实现
│   │
│   └── logx-sdk-spring-boot-starter/  # Spring Boot 集成
│       ├── pom.xml
│       └── src/main/java/com/domidodo/logx/sdk/spring/
│           ├── aspect/                 # AOP 切面
│           │   └── LogAspect.java
│           ├── autoconfigure/          # 自动配置
│           │   └── LogXAutoConfiguration.java
│           ├── context/                # 上下文提供者
│           │   ├── UserContextProvider.java
│           │   └── DefaultUserContextProvider.java
│           ├── properties/             # 配置属性
│           │   └── LogXProperties.java
│           └── resources/META-INF/
│               └── spring.factories    # Spring Boot 自动配置
│
├── logx-gateway/                   # 网关模块 (父项目)
│   ├── pom.xml
│   │
│   ├── logx-gateway-http/         # HTTP 网关
│   │   ├── pom.xml
│   │   └── src/main/
│   │       ├── java/com/domidodo/logx/gateway/http/
│   │       │   ├── GatewayHttpApplication.java
│   │       │   ├── controller/         # 接口控制器
│   │       │   ├── service/            # 业务服务
│   │       │   └── config/             # 配置类
│   │       └── resources/
│   │           └── application.yml
│   │
│   └── logx-gateway-grpc/         # gRPC 网关
│       ├── pom.xml
│       └── src/main/
│           ├── java/com/domidodo/logx/gateway/grpc/
│           │   ├── GatewayGrpcApplication.java
│           │   └── service/            # gRPC 服务实现
│           └── resources/
│               └── application.yml
│
├── logx-engine/                    # 处理引擎 (父项目)
│   ├── pom.xml
│   │
│   ├── logx-engine-processor/     # 日志处理器
│   │   ├── pom.xml
│   │   └── src/main/
│   │       ├── java/com/domidodo/logx/engine/processor/
│   │       │   ├── ProcessorApplication.java
│   │       │   ├── consumer/           # Kafka 消费者
│   │       │   ├── parser/             # 日志解析器
│   │       │   ├── enricher/           # 数据增强
│   │       │   ├── filter/             # 过滤器
│   │       │   └── writer/             # ES 写入器
│   │       └── resources/
│   │           └── application.yml
│   │
│   ├── logx-engine-storage/       # 存储管理
│   │   ├── pom.xml
│   │   └── src/main/
│   │       ├── java/com/domidodo/logx/engine/storage/
│   │       │   ├── StorageApplication.java
│   │       │   ├── config/             # 配置类
│   │       │   │   ├── StorageConfig.java
│   │       │   │   └── MinioConfig.java
│   │       │   ├── elasticsearch/      # ES 管理
│   │       │   │   ├── EsIndexManager.java
│   │       │   │   └── EsTemplateManager.java
│   │       │   ├── lifecycle/          # 生命周期管理
│   │       │   ├── archive/            # 归档服务
│   │       │   └── export/             # 导出服务
│   │       └── resources/
│   │           └── application.yml
│   │
│   └── logx-engine-detection/     # 异常检测
│       ├── pom.xml
│       └── src/main/
│           ├── java/com/domidodo/logx/engine/detection/
│           │   ├── DetectionApplication.java
│           │   ├── rule/               # 规则引擎
│           │   ├── analyzer/           # 分析器
│           │   ├── alert/              # 告警触发
│           │   └── notification/       # 通知服务
│           └── resources/
│               └── application.yml
│
├── logx-console/                   # 管理控制台 (父项目)
│   ├── pom.xml
│   │
│   └── logx-console-api/          # 控制台 API
│       ├── pom.xml
│       └── src/main/
│           ├── java/com/domidodo/logx/console/api/
│           │   ├── ConsoleApiApplication.java
│           │   ├── controller/         # 控制器
│           │   │   ├── LogQueryController.java
│           │   │   ├── SystemController.java
│           │   │   ├── RuleController.java
│           │   │   └── DashboardController.java
│           │   ├── service/            # 服务层
│           │   ├── mapper/             # MyBatis Mapper
│           │   └── entity/             # 实体类
│           └── resources/
│               ├── application.yml
│               └── mapper/             # MyBatis XML
│
└── logx-standalone/                # 单体应用
    ├── pom.xml
    └── src/main/
        ├── java/com/domidodo/logx/
        │   └── StandaloneApplication.java
        └── resources/
            ├── application.yml
            └── banner.txt
```

---

## 📦 模块说明

### 核心层 (Core)

#### logx-common
公共基础模块，提供通用功能：
- **common-core**: 工具类、常量、枚举、异常处理
- **common-api**: DTO、VO 接口定义
- **common-grpc**: Protocol Buffers 协议定义

**依赖**: 被所有其他模块依赖

---

### 基础设施层 (Infrastructure)

#### logx-infrastructure
统一的基础设施配置：
- Elasticsearch 客户端配置
- Kafka 生产者/消费者配置
- Redis 连接配置
- MyBatis Plus 配置 (含多租户插件)
- WebMvc 拦截器配置

**依赖**: logx-common-core

---

### 客户端 SDK 层

#### logx-sdk-core
纯 Java SDK，最小依赖：
- 支持 HTTP/gRPC 双协议
- 内置缓冲区管理
- 异步发送日志
- 自动填充代码位置

**依赖**: fastjson2, slf4j-api

#### logx-sdk-spring-boot-starter
Spring Boot 自动集成：
- 自动配置
- AOP 切面拦截
- 用户上下文自动获取
- 配置属性绑定

**依赖**: logx-sdk-core, Spring Boot

---

### 接入层 (Gateway)

#### logx-gateway-http
HTTP 协议接入：
- RESTful API
- API Key 认证
- 分布式限流 (Redis)
- 发送到 Kafka

**端口**: 10240

#### logx-gateway-grpc
gRPC 协议接入：
- 高性能 RPC
- 流式传输
- Protobuf 序列化

**端口**: 9090

---

### 处理层 (Engine)

#### logx-engine-processor
日志处理器：
- 从 Kafka 消费日志
- 解析、标准化、脱敏
- 批量写入 Elasticsearch
- 性能监控

**Kafka Consumer Group**: `logx-processor-group`

#### logx-engine-storage
存储管理：
- 索引生命周期管理
- 热-温-冷数据分层
- 自动归档到 MinIO
- 数据导出

**定时任务**: 
- 清理: 每天 02:00
- 归档: 每天 03:00

#### logx-engine-detection
异常检测：
- 规则引擎
- 实时分析
- 告警触发
- 多渠道通知

---

### 控制台层 (Console)

#### logx-console-api
管理控制台 API：
- 日志查询与分析
- 系统管理
- 规则配置
- 仪表盘统计

**端口**: 8083
**API 文档**: http://localhost:8083/doc.html

---

### 部署模式

#### logx-standalone
单体应用，包含所有模块：
- HTTP Gateway
- Processor
- Storage
- Detection
- Console API

**端口**: 8080
**适用**: 开发、测试、小规模生产

---

## 🔗 依赖关系

### 编译时依赖

```
logx-common-core
  ↑
  |
logx-infrastructure
  ↑
  |
[logx-gateway, logx-engine, logx-console]
```

### 运行时依赖

```
SDK → Gateway → Kafka → Processor → Elasticsearch
                          ↓
                      Detection → Alert
```

---

## 📄 关键文件说明

### 配置文件

| 文件 | 位置 | 说明 |
|------|------|------|
| pom.xml | 根目录 | Maven 项目配置 |
| docker-compose.yml | 根目录 | 中间件编排 |
| application.yml | 各模块/resources | 应用配置 |
| init.sql | scripts/ | 数据库初始化 |
| spring.factories | SDK starter | Spring Boot 自动配置 |

### Proto 文件

| 文件 | 位置 | 说明 |
|------|------|------|
| log_service.proto | logx-common-grpc | 日志接收服务 |
| query_service.proto | logx-common-grpc | 日志查询服务 |

### 脚本文件

| 文件 | 位置 | 说明 |
|------|------|------|
| start-all.sh | scripts/ | 一键启动 |
| stop-all.sh | scripts/ | 一键停止 |
| health-check.sh | scripts/ | 健康检查 |

---

## 🛠️ 开发指南

### 新增模块

1. 在父 pom.xml 添加 `<module>`
2. 创建子模块目录和 pom.xml
3. 继承父项目配置
4. 添加依赖关系

### 修改协议

1. 编辑 `.proto` 文件
2. 运行 `mvn clean compile` 生成代码
3. 更新 SDK 和 Gateway 实现

### 添加规则

1. 在数据库添加规则记录
2. 在 Detection 模块实现规则逻辑
3. 配置告警通知

---

## 📊 代码统计

| 模块 | Java 文件数 | 代码行数 (估算) |
|------|------------|----------------|
| common | 15 | ~1,500 |
| infrastructure | 10 | ~800 |
| sdk-core | 8 | ~1,200 |
| sdk-starter | 5 | ~600 |
| gateway-http | 10 | ~1,000 |
| gateway-grpc | 5 | ~500 |
| engine-processor | 12 | ~1,500 |
| engine-storage | 10 | ~1,200 |
| engine-detection | 8 | ~1,000 |
| console-api | 15 | ~2,000 |
| **总计** | **~98** | **~11,300** |

---

## 🚀 下一步

1. ✅ 理解项目结构
2. 📚 阅读核心模块代码
3. 🔧 根据需求定制配置
4. 🧪 编写单元测试
5. 🚀 部署到生产环境
