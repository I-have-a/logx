# LogX 模块依赖关系总结

## 核心模块依赖树

### 1. SDK 模块 (供客户端集成)

```
logx-sdk-spring-boot-starter (最常用)
├── logx-sdk-core
│   ├── fastjson2 (2.0.54)
│   ├── slf4j-api
│   └── logx-common-grpc (可选, 启用 gRPC 时)
├── spring-boot-starter
├── spring-boot-starter-aop
└── spring-boot-configuration-processor

logx-sdk-core (纯 Java 应用)
├── fastjson2 (2.0.54)
├── slf4j-api
└── logx-common-grpc (可选)
```

**Maven 依赖：**
```xml
<!-- Spring Boot 应用 -->
<dependency>
    <groupId>com.domidodo</groupId>
    <artifactId>logx-sdk-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>

<!-- 纯 Java 应用 -->
<dependency>
    <groupId>com.domidodo</groupId>
    <artifactId>logx-sdk-core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

---

### 2. 公共模块 (内部依赖)

```
logx-common (父项目)
├── logx-common-core (核心工具类)
│   ├── hutool-all (5.8.34)
│   ├── fastjson2 (2.0.54)
│   ├── druid-spring-boot-3-starter (1.2.27)
│   ├── mysql-connector-j
│   ├── spring-boot-starter-web
│   └── spring-boot-starter-validation
│
├── logx-common-api (API 接口定义)
│   └── logx-common-core
│
└── logx-common-grpc (gRPC 协议)
    ├── grpc-netty-shaded (1.59.0)
    ├── grpc-protobuf (1.59.0)
    └── grpc-stub (1.59.0)
```

---

### 3. 基础设施模块

```
logx-infrastructure
├── logx-common-core
├── spring-boot-starter-web
├── mybatis-plus-spring-boot3-starter (3.5.7)
├── spring-boot-starter-data-redis
├── spring-data-elasticsearch
├── spring-kafka
├── easyexcel (4.0.3)
└── knife4j-openapi3-jakarta-spring-boot-starter (4.5.0)
```

---

### 4. 网关模块

```
logx-gateway-http
├── logx-common-core
├── logx-common-api
├── logx-infrastructure
├── spring-boot-starter-web
├── spring-boot-starter-data-redis
└── redisson-spring-boot-starter (3.37.0)

logx-gateway-grpc
├── logx-common-core
├── logx-common-grpc
├── logx-infrastructure
└── grpc-server-spring-boot-starter (2.15.0.RELEASE)
```

---

### 5. 处理引擎模块

```
logx-engine-processor (日志处理器)
├── logx-common-core
├── logx-infrastructure
├── logx-engine-storage
├── spring-boot-starter
├── spring-kafka
├── spring-boot-starter-data-elasticsearch
└── micrometer-core

logx-engine-storage (存储管理)
├── logx-common-core
├── logx-infrastructure
└── (Elasticsearch, MinIO 通过 infrastructure)

logx-engine-detection (异常检测)
├── logx-common-core
├── logx-infrastructure
└── (Elasticsearch 通过 infrastructure)
```

---

### 6. 管理控制台

```
logx-console-api
├── logx-common-core
├── logx-common-api
├── logx-infrastructure
└── (MyBatis Plus, ES 通过 infrastructure)
```

---

### 7. 单体应用

```
logx-standalone (集成所有服务)
├── logx-gateway-http
├── logx-engine-processor
├── logx-engine-storage
├── logx-engine-detection
└── logx-console-api
```

---

## 依赖版本总览

### 框架版本

| 框架 | 版本 | 说明 |
|------|------|------|
| JDK | 17 | 最低版本要求 |
| Spring Boot | 3.2.2 | 主框架 |
| Spring Cloud | 2023.0.0 | 微服务组件 (可选) |
| Maven | 3.8+ | 构建工具 |

### 核心依赖

| 组件 | 版本 | 用途 |
|------|------|------|
| mybatis-plus | 3.5.7 | ORM 框架 |
| druid | 1.2.27 | 数据库连接池 |
| hutool | 5.8.34 | Java 工具集 |
| fastjson2 | 2.0.54 | JSON 处理 |
| knife4j | 4.5.0 | API 文档 |
| redisson | 3.37.0 | Redis 客户端 |
| easyexcel | 4.0.3 | Excel 处理 |

### gRPC 依赖 (可选)

| 组件 | 版本 | 用途 |
|------|------|------|
| grpc | 1.59.0 | gRPC 核心 |
| protobuf | 3.24.0 | 协议缓冲 |
| grpc-spring-boot | 2.15.0.RELEASE | Spring Boot 集成 |

---

## 中间件要求

### 必需中间件

| 中间件 | 最低版本 | 推荐版本 | 最小资源 | 用途 |
|--------|---------|---------|---------|------|
| MySQL | 5.7+ | 8.0+ | 512MB | 配置存储 |
| Elasticsearch | 7.x+ | 8.11.0+ | 2GB | 日志存储 |
| Kafka | 2.8+ | 3.5+ | 1GB | 消息队列 |
| Redis | 5.0+ | 7.0+ | 256MB | 缓存/限流 |

### 可选中间件

| 中间件 | 版本 | 用途 |
|--------|------|------|
| MinIO | 最新 | 冷数据归档 |
| Zookeeper | 3.8+ | Kafka 依赖 |

---

## 部署资源要求

### 单体模式 (最小配置)

```
CPU:    2 核
内存:   8GB
  - JVM:           2GB (logx-standalone)
  - MySQL:         512MB
  - Redis:         256MB
  - Elasticsearch: 2GB
  - Kafka:         1GB
  - 系统预留:       2GB
磁盘:   20GB (建议 SSD)
```

### 微服务模式 (推荐配置)

```
CPU:    4 核+
内存:   16GB
  - Gateway (HTTP):      1GB
  - Gateway (gRPC):      1GB (可选)
  - Engine Processor:    2GB
  - Engine Storage:      1GB
  - Engine Detection:    1GB
  - Console API:         2GB
  - MySQL:              1GB
  - Redis:              512MB
  - Elasticsearch:      4GB
  - Kafka:              2GB
  - 系统预留:            2GB
磁盘:   100GB SSD
```

---

## 快速依赖检查清单

### 开发环境

- [ ] JDK 17 安装
- [ ] Maven 3.8+ 安装
- [ ] Docker & Docker Compose 安装
- [ ] 8GB+ 可用内存
- [ ] 20GB+ 可用磁盘

### 客户端集成

- [ ] 添加 SDK 依赖 (starter 或 core)
- [ ] 配置租户 ID、系统 ID
- [ ] 配置网关地址
- [ ] (可选) 配置 gRPC

### 服务端部署

- [ ] MySQL 启动并初始化
- [ ] Redis 启动
- [ ] Elasticsearch 启动
- [ ] Kafka + Zookeeper 启动
- [ ] 至少启动 Gateway + Processor
- [ ] (可选) 启动 Console API 查看日志

---

## 常见依赖问题

### Q1: SDK 依赖冲突

**问题：** FastJSON 版本冲突
```
Caused by: java.lang.NoSuchMethodError: com.alibaba.fastjson2.JSON.toJSONString
```

**解决：**
```xml
<!-- 排除旧版本 -->
<dependency>
    <groupId>com.domidodo</groupId>
    <artifactId>logx-sdk-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <exclusions>
        <exclusion>
            <groupId>com.alibaba</groupId>
            <artifactId>fastjson</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### Q2: gRPC 依赖问题

**问题：** gRPC 版本不兼容
```
io.grpc.StatusRuntimeException: UNAVAILABLE
```

**解决：** 统一使用根 pom 定义的版本
```xml
<properties>
    <grpc.version>1.59.0</grpc.version>
</properties>
```

### Q3: Spring Boot 3.x 兼容性

**注意：** 使用 Spring Boot 3.x 需要：
- JDK 17+
- Jakarta EE (不是 javax)
- MyBatis Plus 3.5.7+ (支持 Spring Boot 3)

---

## 依赖优化建议

### 1. 生产环境优化

```xml
<!-- 排除开发时依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

### 2. 减少 SDK 体积

如果只需要 HTTP 协议，可以排除 gRPC：
```xml
<dependency>
    <groupId>com.domidodo</groupId>
    <artifactId>logx-sdk-core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <exclusions>
        <exclusion>
            <groupId>com.domidodo</groupId>
            <artifactId>logx-common-grpc</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### 3. 按需引入中间件

不使用归档功能时，可以不启动 MinIO。 

不使用 gRPC 时，可以不部署 logx-gateway-grpc。
