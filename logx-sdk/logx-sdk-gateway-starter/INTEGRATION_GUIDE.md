# LogX SDK 微服务网关集成指南

## 一、架构概览

```
                                    ┌─────────────────┐
                                    │   LogX Server   │
                                    │  (日志平台服务)  │
                                    └────────▲────────┘
                                             │
              ┌──────────────────────────────┼──────────────────────────────┐
              │                              │                              │
        ┌─────┴─────┐                  ┌─────┴─────┐                 ┌─────┴─────┐
        │  domidodo │                  │  realauth │                 │   order   │
        │  gateway  │                  │  service  │                 │  service  │
        │(网关日志) │                  │(业务日志) │                 │(业务日志) │
        └─────┬─────┘                  └─────▲─────┘                 └─────▲─────┘
              │                              │                              │
              │         X-Trace-Id           │         X-Trace-Id           │
              └──────────────────────────────┴──────────────────────────────┘
                                             │
                                    ┌────────┴────────┐
                                    │     Client      │
                                    └─────────────────┘
```

**日志关联流程：**
1. 请求进入 `domidodo-gateway`，生成 `TraceId`
2. 网关记录入口日志，将 `TraceId` 注入请求头传递给下游
3. 下游服务（如 `realauth`）接收 `TraceId`，所有日志带上该 ID
4. LogX 日志平台通过 `TraceId` 关联完整调用链

---

## 二、SDK 模块说明

```
logx-sdk/
├── logx-sdk-core/                    # 核心模块（共享）
│   └── TraceContext.java             # ★ 追踪上下文（核心共享类）
│
├── logx-sdk-gateway-starter/         # 网关专用（WebFlux）
│   ├── LogXGatewayFilter.java        # 全局过滤器
│   ├── LogXGatewayProperties.java    # 网关配置
│   └── LogXGatewayAutoConfiguration  # 自动配置
│
└── logx-sdk-spring-boot-starter/     # 业务服务专用（Servlet）
    ├── TraceIdFilter.java            # ★ 接收 TraceId 过滤器
    ├── LogAspect.java                # ★ 从 TraceContext 获取 TraceId
    ├── LogXProperties.java           # 服务配置
    └── LogXAutoConfiguration.java    # 自动配置
```

---

## 三、网关集成（domidodo-gateway）

### 3.1 添加依赖

```xml
<!-- domidodo-gateway/pom.xml -->
<dependency>
    <groupId>com.domidodo</groupId>
    <artifactId>logx-sdk-gateway-starter</artifactId>
    <version>${logx.version}</version>
</dependency>
```

### 3.2 配置文件

```yaml
# application.yml 或 Nacos 配置
logx:
  gateway:
    enabled: true
    
    # ============ 必填配置 ============
    tenant-id: "your-tenant-id"
    system-id: "domidodo-gateway"
    system-name: "API网关"
    api-key: "your-api-key"
    
    # ============ LogX 服务端 ============
    mode: grpc  # http 或 grpc
    server:
      # HTTP 模式
      url: http://logx-server:8080
      # gRPC 模式
      host: logx-server
      port: 9090
      batch-mode: stream
    
    # ============ 分布式追踪 ============
    trace:
      enabled: true
      trace-id-header: X-Trace-Id
      span-id-header: X-Span-Id
      propagate-user: true  # 传递用户信息到下游
    
    # ============ 日志配置 ============
    log:
      exclude-path-prefixes:
        - /actuator
        - /health
      slow-threshold: 5000
    
    # ============ 缓冲配置 ============
    buffer:
      enabled: true
      size: 1000
      flush-interval: 5s
```

### 3.3 启动日志

配置正确后，启动时会看到：

```
LogX Gateway SDK 使用 gRPC 模式 [logx-server:9090]
LogX Gateway SDK 初始化完成 [租户:xxx, 系统:API网关, 追踪:true]
启用 LogX 网关过滤器 [排除前缀:[/actuator, /health], 慢请求阈值:5000ms]
```

---

## 四、业务服务集成（如 domidodo-realauth）

### 4.1 添加依赖

你的 pom.xml 已经有了：

```xml
<dependency>
    <groupId>com.domidodo</groupId>
    <artifactId>logx-sdk-spring-boot-starter</artifactId>
</dependency>
```

### 4.2 配置文件

```yaml
# application.yml 或 Nacos 配置
logx:
  enabled: true
  
  # ============ 必填配置 ============
  tenant-id: "your-tenant-id"
  system-id: "domidodo-realauth"
  system-name: "实名认证服务"
  api-key: "your-api-key"
  
  # ============ LogX 服务端 ============
  mode: grpc
  gateway:
    host: logx-server
    port: 9090
    batch-mode: stream
  
  # ============ 分布式追踪（关键！） ============
  trace:
    enabled: true  # 启用后自动接收网关传递的 TraceId
  
  # ============ AOP 日志收集 ============
  aspect:
    enabled: true
    controller: true
    service: false
    slow-threshold: 5000
  
  # ============ 用户上下文 ============
  user-context:
    enabled: true
    source:
      - header  # 优先从 Header 获取（网关传递）
      - session
      - principal
    # Header 名称（与网关保持一致）
    trace-id-header: X-Trace-Id
    span-id-header: X-Span-Id
    user-id-header: X-User-Id
    user-name-header: X-User-Name
  
  # ============ 缓冲配置 ============
  buffer:
    enabled: true
    size: 1000
    flush-interval: 5s
```

### 4.3 启动日志

配置正确后，启动时会看到：

```
LogX SDK 使用 gRPC 模式 [logx-server:9090]
注册 LogX TraceId 过滤器
LogX SDK 初始化完成 [租户:xxx, 系统:实名认证服务, 追踪:true, 缓冲:true]
启用 LogX AOP 自动日志收集 [controller=true, service=false, slowThreshold=5000ms]
```

---

## 五、验证集成

### 5.1 发送测试请求

```bash
curl -X GET http://localhost:8080/api/realauth/test \
     -H "X-User-Id: user123" \
     -H "X-User-Name: 张三"
```

### 5.2 检查日志输出

**网关日志（DEBUG 级别）：**
```
[abc12345] GET /api/realauth/test -> 200 (50ms)
```

**业务服务日志（DEBUG 级别）：**
```
设置追踪上下文 [traceId=abc12345, spanId=def67890, userId=user123]
```

### 5.3 在 LogX 日志平台查看

| TraceId | 系统 | 级别 | 消息 | 响应时间 |
|---------|------|------|------|----------|
| abc12345... | API网关 | INFO | GET /api/realauth/test -> 200 | 50ms |
| abc12345... | 实名认证服务 | INFO | RealAuthController.test() 执行完成 | 30ms |

**同一个 TraceId 关联了网关和业务服务的日志！**

---

## 六、需要修改的现有文件

根据你的项目结构，需要更新以下文件：

### logx-sdk-core 模块

| 文件 | 操作 | 说明 |
|------|------|------|
| `TraceContext.java` | **新增** | 追踪上下文管理（共享） |

### logx-sdk-gateway-starter 模块

| 文件 | 操作 | 说明 |
|------|------|------|
| `LogXGatewayFilter.java` | **替换** | 使用 core 的 TraceContext |
| `LogXGatewayProperties.java` | **替换** | 完整配置 |
| `LogXGatewayAutoConfiguration.java` | **替换** | 自动配置 |

### logx-sdk-spring-boot-starter 模块

| 文件 | 操作 | 说明 |
|------|------|------|
| `TraceIdFilter.java` | **新增** | 接收网关 TraceId |
| `TraceIdFilterConfiguration.java` | **新增** | 注册过滤器 |
| `LogXProperties.java` | **替换** | 添加 trace 配置 |
| `LogAspect.java` | **替换** | 从 TraceContext 获取 TraceId |
| `LogXAutoConfiguration.java` | **替换** | 导入 TraceIdFilterConfiguration |

---

## 七、Nacos 共享配置建议

建议在 Nacos 创建共享配置 `logx-shared.yml`：

```yaml
# 所有服务共享
logx:
  tenant-id: "your-tenant-id"
  api-key: "your-api-key"
  mode: grpc
  gateway:
    host: logx-server
    port: 9090
  trace:
    enabled: true
  buffer:
    enabled: true
    size: 1000
    flush-interval: 5s
```

各服务只需覆盖自己的标识：

```yaml
# domidodo-realauth
logx:
  system-id: "domidodo-realauth"
  system-name: "实名认证服务"
```

---

## 八、常见问题

### Q1: TraceId 没有传递到下游服务？

检查：
1. 网关 `logx.gateway.trace.enabled=true`
2. 业务服务 `logx.trace.enabled=true`
3. Header 名称是否一致（默认 `X-Trace-Id`）

### Q2: 日志没有关联？

检查：
1. 网关和服务的 `tenant-id` 是否一致
2. 是否都连接到同一个 LogX 服务端

### Q3: 网关报错 "不支持 HttpServletRequest"？

确保网关使用 `logx-sdk-gateway-starter`，而不是 `logx-sdk-spring-boot-starter`。

### Q4: 业务服务没有收到用户信息？

检查网关配置 `logx.gateway.trace.propagate-user=true`。

---

## 九、后续优化建议

1. **Dubbo 追踪传递**：为 Dubbo RPC 添加 Filter，传递 TraceId
2. **异步任务追踪**：使用 `TransmittableThreadLocal` 支持线程池
3. **MQ 消息追踪**：在消息头中传递 TraceId
4. **日志采样**：高流量场景下按比例采样
