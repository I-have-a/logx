# LogX SDK 配置文档

## 概述

LogX SDK 是一个企业级日志收集客户端，支持 HTTP 和 gRPC 两种传输模式，提供 AOP 自动日志收集和灵活的用户上下文获取机制。

## 完整配置示例

```yaml
logx:
  # ==================== 基础配置 ====================
  enabled: true                    # 是否启用 LogX SDK
  tenant-id: "tenant_001"          # 租户ID（必填）
  system-id: "sys_001"             # 系统ID（必填）
  system-name: "订单服务"           # 系统名称（必填）
  api-key: "your-api-key"          # API密钥（必填，由服务端生成）
  mode: http                       # 通信模式: http | grpc

  # ==================== 网关配置 ====================
  gateway:
    # HTTP 模式配置
    url: http://localhost:8080     # HTTP 网关地址

    # gRPC 模式配置
    host: localhost                # gRPC 服务主机
    port: 9090                     # gRPC 服务端口
    batch-mode: stream             # gRPC 批量传输模式: batch | stream

    # 超时配置
    connect-timeout: 5000          # 连接超时（毫秒）
    read-timeout: 5000             # 读取超时（毫秒）

  # ==================== 缓冲配置 ====================
  buffer:
    enabled: true                  # 是否启用缓冲（批量发送）
    size: 1000                     # 缓冲区大小（条数）
    flush-interval: 5s             # 刷新间隔（支持 Duration 格式）

  # ==================== AOP 切面配置 ====================
  aspect:
    enabled: true                  # 是否启用 AOP 自动日志收集
    controller: true               # 是否收集 Controller 层日志
    service: false                 # 是否收集 Service 层日志
    log-args: true                 # 是否记录方法入参
    log-result: true               # 是否记录方法返回值
    slow-threshold: 5000           # 慢请求阈值（毫秒）

  # ==================== 用户上下文配置 ====================
  user-context:
    enabled: true                  # 是否启用用户上下文自动获取
    source: # 获取来源（按顺序尝试）
      - header
      - session
      - principal
      - parameter

    # 请求头配置
    user-id-header: X-User-Id      # 用户ID请求头名称
    user-name-header: X-User-Name  # 用户名请求头名称
    tenant-id-header: X-Tenant-Id  # 租户ID请求头名称

    # Session 配置
    user-id-session-key: userId    # Session中用户ID的键名
    user-name-session-key: userName # Session中用户名的键名
    tenant-id-session-key: tenantId # Session中租户ID的键名

    # 请求参数配置
    user-id-parameter: userId      # 用户ID请求参数名称

    # 自定义提供器
    custom-provider-bean-name:     # 自定义 UserContextProvider Bean 名称

  # ==================== 模块配置 ====================
  module:
    enabled: true
    # 默认模块名（无法确定时使用）
    default-module: "未分类"
    # 是否使用简化类名作为模块名
    # true: OrderController -> order
    # false: 使用包名提取
    use-simple-class-name: false
    # 包名到模块名的映射（最长前缀匹配）
    package-mapping:
      com.example.order: 订单模块
      com.example.user: 用户模块
      com.example.product: 商品模块
      com.example.payment: 支付模块
      com.example.report: 报表模块

    # 完整类名到模块名的映射
    class-mapping:
      com.example.order.controller.OrderController: 订单管理
      com.example.order.controller.OrderQueryController: 订单查询
      com.example.order.controller.OrderExportController: 订单导出
```

---

## 配置项详解

### 1. 基础配置

| 配置项                | 类型      | 默认值    | 必填    | 说明                     |
|--------------------|---------|--------|-------|------------------------|
| `logx.enabled`     | boolean | `true` | 否     | 总开关，设为 false 将完全禁用 SDK |
| `logx.tenant-id`   | String  | -      | **是** | 租户标识，用于多租户隔离           |
| `logx.system-id`   | String  | -      | **是** | 系统唯一标识                 |
| `logx.system-name` | String  | -      | **是** | 系统显示名称                 |
| `logx.api-key`     | String  | -      | **是** | 认证密钥，由 LogX 服务端生成      |
| `logx.mode`        | String  | `http` | 否     | 通信模式：`http` 或 `grpc`   |

### 2. 网关配置 (gateway)

#### HTTP 模式

| 配置项                | 类型     | 默认值                     | 说明          |
|--------------------|--------|-------------------------|-------------|
| `logx.gateway.url` | String | `http://localhost:8080` | HTTP 网关完整地址 |

#### gRPC 模式

| 配置项                       | 类型     | 默认值         | 说明                                  |
|---------------------------|--------|-------------|-------------------------------------|
| `logx.gateway.host`       | String | `localhost` | gRPC 服务主机地址                         |
| `logx.gateway.port`       | int    | `9090`      | gRPC 服务端口                           |
| `logx.gateway.batch-mode` | String | `stream`    | 批量传输模式：`batch`（批量RPC）或 `stream`（流式） |

#### 超时配置

| 配置项                            | 类型  | 默认值    | 说明         |
|--------------------------------|-----|--------|------------|
| `logx.gateway.connect-timeout` | int | `5000` | 连接超时时间（毫秒） |
| `logx.gateway.read-timeout`    | int | `5000` | 读取超时时间（毫秒） |

### 3. 缓冲配置 (buffer)

| 配置项                          | 类型       | 默认值    | 说明                             |
|------------------------------|----------|--------|--------------------------------|
| `logx.buffer.enabled`        | boolean  | `true` | 是否启用缓冲，启用后日志会先存入内存再批量发送        |
| `logx.buffer.size`           | int      | `1000` | 缓冲区大小，达到此数量后自动刷新               |
| `logx.buffer.flush-interval` | Duration | `5s`   | 定时刷新间隔，支持 `5s`、`1m`、`PT5S` 等格式 |

### 4. AOP 切面配置 (aspect)

| 配置项                          | 类型      | 默认值     | 说明                                      |
|------------------------------|---------|---------|-----------------------------------------|
| `logx.aspect.enabled`        | boolean | `true`  | 是否启用 AOP 自动日志收集                         |
| `logx.aspect.controller`     | boolean | `true`  | 是否自动收集 @RestController/@Controller 方法日志 |
| `logx.aspect.service`        | boolean | `false` | 是否自动收集 @Service 方法日志                    |
| `logx.aspect.log-args`       | boolean | `true`  | 是否记录方法入参（敏感信息建议关闭）                      |
| `logx.aspect.log-result`     | boolean | `true`  | 是否记录方法返回值（大对象建议关闭）                      |
| `logx.aspect.slow-threshold` | long    | `5000`  | 慢请求阈值（毫秒），超过此值记录 WARN 级别日志              |

### 5. 用户上下文配置 (user-context)

| 配置项                                           | 类型      | 默认值                            | 说明                                |
|-----------------------------------------------|---------|--------------------------------|-----------------------------------|
| `logx.user-context.enabled`                   | boolean | `true`                         | 是否启用用户上下文自动获取                     |
| `logx.user-context.source`                    | List    | `[header, session, principal]` | 获取来源优先级列表                         |
| `logx.user-context.custom-provider-bean-name` | String  | -                              | 自定义 UserContextProvider 的 Bean 名称 |

#### 请求头方式

| 配置项                                  | 类型     | 默认值           |
|--------------------------------------|--------|---------------|
| `logx.user-context.user-id-header`   | String | `X-User-Id`   |
| `logx.user-context.user-name-header` | String | `X-User-Name` |
| `logx.user-context.tenant-id-header` | String | `X-Tenant-Id` |

#### Session 方式

| 配置项                                       | 类型     | 默认值        |
|-------------------------------------------|--------|------------|
| `logx.user-context.user-id-session-key`   | String | `userId`   |
| `logx.user-context.user-name-session-key` | String | `userName` |
| `logx.user-context.tenant-id-session-key` | String | `tenantId` |

#### 请求参数方式

| 配置项                                   | 类型     | 默认值      |
|---------------------------------------|--------|----------|
| `logx.user-context.user-id-parameter` | String | `userId` |

### 6. 模块配置 (module)

| 配置项                                 | 类型      | 默认值       | 说明                |
|-------------------------------------|---------|-----------|-------------------|
| `logx.module.enabled`               | boolean | `true`    | 是否启用模块映射          |
| `logx.module.default-module`        | String  | `default` | 默认模块名（当无法确定模块时使用） |
| `logx.module.use-simple-class-name` | boolean | `false`   | 是否使用简化的类名作为模块名    |
| `logx.module.package-mapping`       | Map     |           | 包名到模块名的映射         |
| `logx.module.class-mapping`         | Map     |           | 类名到模块名的映射（全限定类名）  |

---

## 使用场景配置示例

### 场景一：最小化配置

```yaml
logx:
  tenant-id: "default"
  system-id: "my-app"
  system-name: "我的应用"
  api-key: "xxx"
```

### 场景二：gRPC 高性能模式

```yaml
logx:
  tenant-id: "tenant_001"
  system-id: "order-service"
  system-name: "订单服务"
  api-key: "xxx"
  mode: grpc
  gateway:
    host: logx-gateway.internal
    port: 9090
    batch-mode: stream
    connect-timeout: 3000
  buffer:
    size: 2000
    flush-interval: 3s
```

### 场景三：自定义用户上下文（JWT Token）

```yaml
logx:
  tenant-id: "tenant_001"
  system-id: "user-service"
  system-name: "用户服务"
  api-key: "xxx"
  user-context:
    custom-provider-bean-name: jwtUserContextProvider
```

```java

@Component("jwtUserContextProvider")
public class JwtUserContextProvider implements UserContextProvider {

    @Override
    public String getUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        // 解析 JWT 获取用户ID
        return JwtUtils.getUserId(token);
    }

    @Override
    public String getUserName(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return JwtUtils.getUserName(token);
    }
}
```

### 场景四：生产环境推荐配置

```yaml
logx:
  tenant-id: "${LOGX_TENANT_ID}"
  system-id: "${spring.application.name}"
  system-name: "${LOGX_SYSTEM_NAME:未命名服务}"
  api-key: "${LOGX_API_KEY}"
  mode: grpc
  gateway:
    host: ${LOGX_GATEWAY_HOST:logx-gateway}
    port: ${LOGX_GATEWAY_PORT:9090}
    connect-timeout: 3000
    read-timeout: 5000
  buffer:
    enabled: true
    size: 1000
    flush-interval: 5s
  aspect:
    enabled: true
    controller: true
    service: false
    log-args: true
    log-result: false      # 生产环境建议关闭，避免敏感数据泄露
    slow-threshold: 3000   # 3秒以上视为慢请求
  user-context:
    enabled: true
    source:
      - header
      - principal
```

---

## 注意事项

1. **必填项**：`tenant-id`、`system-id`、`system-name`、`api-key` 缺一不可
2. **模式选择**：内网环境推荐 gRPC 模式，跨网络或防火墙限制时使用 HTTP 模式
3. **缓冲配置**：生产环境建议开启缓冲，避免频繁网络请求
4. **敏感信息**：如果方法参数或返回值包含敏感信息，建议关闭 `log-args` 和 `log-result`
5. **慢请求阈值**：根据业务特点调整，API 接口建议 1-3 秒，后台任务可适当放宽
