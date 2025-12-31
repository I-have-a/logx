# LogX 项目文档总索引

欢迎使用 LogX 企业级日志管理与分析平台! 本目录包含完整的项目文档。

---

## 📚 文档导航

### 🚀 快速开始

| 文档                                  | 说明           | 适用人群 | 预计时间   |
|-------------------------------------|--------------|------|--------|
| **[快速开始指南](./LogX-Quick-Start.md)** | 5分钟快速部署和测试   | 新手   | 5-10分钟 |
| **[项目说明](./LogX-README-v2.md)**     | 完整的项目介绍和使用指南 | 所有人  | 30分钟   |

### 📖 深入理解

| 文档                                           | 说明              | 适用人群   |
|----------------------------------------------|-----------------|--------|
| **[项目结构](./LogX-Project-Structure.md)**      | 完整的目录结构和模块说明    | 开发者    |
| **[依赖关系](./LogX-Dependencies.md)**           | 模块依赖树和版本说明      | 开发者    |
| **[配置详解](./LogX-Configuration-Guide.md)**    | 所有配置项的详细说明      | 运维人员   |
| **[SDK详解](./LogX-SDK-Guide.md)**             | SDK模块完整技术文档     | 集成开发者  |
| **[Engine详解](./LogX-Engine-Guide.md)**       | 日志处理引擎技术文档      | 核心开发者  |
| **[Storage详解](./LogX-Storage-Guide.md)**     | 存储和归档技术文档       | 运维/开发者 |
| **[Detection详解](./LogX-Detection-Guide.md)** | 监控告警技术文档        | 运维/开发者 |
| **[Gateway详解](./LogX-Gateway-Guide.md)**     | HTTP/gRPC网关技术文档 | 核心开发者  |
| **[代码示例](./LogX-Code-Examples.md)**          | 完整的使用案例和示例      | 所有开发者  |
| **[测试指南](./LogX-Testing-Guide.md)**          | 单元测试和集成测试       | 测试/开发者 |

### 📐 架构设计

| 文件                                              | 说明        | 工具      |
|-------------------------------------------------|-----------|---------|
| **[架构图](../scripts/logx-architecture.mermaid)** | 模块依赖关系可视化 | Mermaid |

### 🛠️ 部署文件

| 文件                                              | 说明          | 用途       |
|-------------------------------------------------|-------------|----------|
| **[docker-compose.yml](../docker-compose.yml)** | Docker 编排配置 | 一键启动中间件  |
| **[init.sql](../scripts/init.sql)**             | 数据库初始化脚本    | 创建表和测试数据 |

---

## 📋 文档清单

### 1. LogX-Quick-Start.md

**快速开始指南** - 5分钟上手

**内容**:

- ✅ 环境准备
- ✅ 一键启动中间件
- ✅ 初始化数据库
- ✅ 编译和运行
- ✅ 发送和查询日志
- ✅ 故障排查

**适合**: 刚接触项目的新手

---

### 2. LogX-README-v2.md

**完整项目说明** - 全面介绍

**内容**:

- 📖 项目简介和核心特性
- 🏗️ 模块架构和依赖关系
- 🔧 技术栈详细说明
- 📦 单体/微服务部署指南
- 💻 SDK 集成方式 (Spring Boot / 纯Java)
- 🔐 最低配置要求
- 📊 核心 API 使用
- 🛠️ 常见问题

**适合**: 所有使用者

---

### 3. LogX-Project-Structure.md

**项目结构说明** - 代码组织

**内容**:

- 📂 完整目录树
- 📦 模块功能说明
- 🔗 依赖关系图
- 📄 关键文件说明
- 🛠️ 开发指南
- 📊 代码统计

**适合**: 需要理解代码结构的开发者

---

### 4. LogX-Dependencies.md

**依赖关系详解** - 技术栈

**内容**:

- 📦 所有模块的详细依赖树
- 🔢 版本总览表
- 🐳 中间件资源要求
- 💾 部署资源规划
- ⚠️ 常见依赖问题和解决方案
- 🔧 优化建议

**适合**: 开发者和架构师

---

### 5. LogX-Configuration-Guide.md

**配置详解** - 运维必读

**内容**:

- ⚙️ 完整的 application.yml 示例
- 🐳 中间件配置详解 (MySQL, ES, Kafka, Redis, MinIO)
- 📊 Elasticsearch 索引设计
- 🔌 gRPC 协议说明
- 📜 一键部署脚本
- 📈 监控与运维
- 🔧 性能优化建议

**适合**: 运维人员和架构师

---

### 6. logx-architecture.mermaid

**架构图** - 可视化设计

**内容**:

- 模块依赖关系
- 数据流向
- 中间件交互

**查看方式**:

- 使用支持 Mermaid 的编辑器 (VS Code + 插件)
- 在线工具: https://mermaid.live/
- GitHub/GitLab 自动渲染

---

### 7. docker-compose.yml

**Docker 编排文件** - 中间件配置

**包含服务**:

- MySQL 8.0.44
- Redis 7.2
- Elasticsearch 7.17.15
- Kafka 3.7.0 (KRaft 模式)
- MinIO (对象存储)
- Kibana (可选)

**使用**:

```bash
docker-compose up -d
```

---

### 8. init.sql

**数据库初始化脚本** - 建表和测试数据

**包含表**:

- sys_tenant (租户表)
- sys_system (系统表)
- log_exception_rule (异常规则)
- log_notification_config (通知配置)
- log_alert_record (告警记录)

**使用**:

```bash
docker exec -i logx-mysql mysql -uroot -proot123 < init.sql
```

---

### 9. LogX-SDK-Guide.md

**SDK模块技术文档** - 客户端集成指南

**内容**:

- 📦 SDK架构和组件
- 🔧 Spring Boot自动配置原理
- 📝 LogEntry完整字段说明
- 🚀 HTTP vs gRPC性能对比
- 🔒 Protobuf Struct支持
- 🎯 AOP自动拦截机制
- 👤 用户上下文自动获取
- 💡 最佳实践和性能优化

**适合**: 需要集成SDK的开发者

---

### 10. LogX-Engine-Guide.md

**Engine模块技术文档** - 日志处理引擎

**内容**:

- 🔄 完整的日志处理流程
- 📊 Kafka批量消费机制
- 🔐 LogParser脱敏规则详解
- 💾 ElasticsearchWriter批量写入
- 🔁 重试机制和死信队列
- 📈 性能优化和资源规划
- 📉 监控指标和故障排查
- ⚙️ 完整配置示例

**适合**: 核心开发者和运维人员

---

### 11. LogX-Code-Examples.md

**代码示例和使用案例** - 实战指南

**内容**:

- 🚀 3行代码快速开始
- 🌱 Spring Boot完整集成
- ☕ 纯Java集成示例
- 🎨 高级特性使用
- 🏪 实际业务场景案例
- 🧪 单元测试和性能测试
- 📋 常见模式总结
- ✅ 最佳实践清单

**适合**: 所有开发者

---

### 12. LogX-Storage-Guide.md

**Storage模块技术文档** - 存储和归档

**内容**:

- 📦 数据生命周期管理（热温冷）
- 🔧 HotColdStrategy策略详解
- 📊 ES模板管理（25字段映射）
- 💾 数据导出归档（Scroll API）
- 🚀 分块导出（ChunkedDataExporter）
- ⚡ 批量并发导出（BatchExportService）
- ⏰ 定时清理任务
- 📈 存储成本估算

**适合**: 运维人员和核心开发者

---

### 13. LogX-Detection-Guide.md

**Detection模块技术文档** - 监控告警

**内容**:

- 🎯 5种规则类型详解
- 📝 字段值比较规则（FIELD_COMPARE）
- 📊 批量操作监控（BATCH_OPERATION）
- 🔄 连续请求监控（CONTINUOUS_REQUEST）
- ⚡ 响应时间监控（RESPONSE_TIME）
- ❌ 错误率监控（ERROR_RATE）
- 🔔 告警服务和通知策略
- 📧 多渠道通知（邮件/短信/Webhook）
- 💡 规则配置最佳实践

**适合**: 运维人员和开发者

---

### 14. LogX-Gateway-Guide.md

**Gateway模块技术文档** - HTTP/gRPC网关

**内容**:

- 🌐 HTTP网关（LogIngestController + Service）
- 🚀 gRPC网关（LogIngestGrpcService）
- 🚦 三级限流机制（全局/租户/系统）
- 🔐 API Key认证授权
- 📊 Protobuf Struct完整转换
- 📡 Kafka批量发送（30秒超时）
- ⚡ 性能对比（HTTP vs gRPC: 2.2x）
- 🔧 完整配置指南

**适合**: 核心开发者和架构师

---

### 15. LogX-Testing-Guide.md

**测试指南** - 单元测试和集成测试

**内容**:

- 🧪 Protobuf Struct完整测试（12个场景）
- 🎯 规则检测完整测试（15个场景）
- 🔗 端到端数据流测试（6个场景）
- ⚡ 性能测试（SDK/Detection/Gateway）
- 📋 测试最佳实践
- 🐛 故障排查指南
- ✅ 边界情况测试

**适合**: 测试工程师和开发者

---

## 🗺️ 学习路径

### 新手入门 (第1天)

1. ✅ 阅读 [快速开始指南](./LogX-Quick-Start.md)
2. ✅ 启动中间件 (docker-compose)
3. ✅ 运行单体应用
4. ✅ 发送和查询日志

### 深入理解 (第2-3天)

1. 📖 阅读 [项目说明](./LogX-README-v2.md)
2. 📂 理解 [项目结构](./LogX-Project-Structure.md)
3. 🔗 学习 [依赖关系](./LogX-Dependencies.md)
4. 💻 研读 [SDK详解](./LogX-SDK-Guide.md)
5. 🔧 理解 [Engine详解](./LogX-Engine-Guide.md)
6. 📝 实践 [代码示例](./LogX-Code-Examples.md)

### 高级功能 (第4-5天)

1. 💾 学习 [Storage详解](./LogX-Storage-Guide.md) - 数据归档
2. 🔔 学习 [Detection详解](./LogX-Detection-Guide.md) - 监控告警
3. 🌐 学习 [Gateway详解](./LogX-Gateway-Guide.md) - HTTP/gRPC网关
4. ⚙️ 研读 [配置详解](./LogX-Configuration-Guide.md)
5. 🚀 尝试不同部署模式
6. 📊 配置监控和告警规则

### 测试实践 (第6天)

1. 🧪 阅读 [测试指南](./LogX-Testing-Guide.md)
2. 📝 运行单元测试
3. 🔗 执行端到端测试
4. ⚡ 进行性能测试
5. 🐛 学习故障排查

### 生产部署 (第4-5天)

1. ⚙️ 研究 [配置详解](./LogX-Configuration-Guide.md)
2. 🚀 部署微服务模式
3. 📈 配置监控告警
4. 🔧 性能优化

### 二次开发 (长期)

1. 💻 熟悉核心代码
2. 🔌 定制业务规则
3. 📦 扩展功能模块
4. 🧪 编写单元测试

---

## 📊 文档版本

| 文档          | 版本   | 更新日期       | 说明     |
|-------------|------|------------|--------|
| 快速开始        | v1.0 | 2024-12-27 | 初始版本   |
| 项目说明        | v2.0 | 2024-12-27 | 完整重构   |
| 项目结构        | v1.0 | 2024-12-27 | 初始版本   |
| 依赖关系        | v1.0 | 2024-12-27 | 初始版本   |
| 配置详解        | v1.0 | 2024-12-27 | 初始版本   |
| SDK详解       | v1.0 | 2024-12-27 | 基于真实代码 |
| Engine详解    | v1.0 | 2024-12-27 | 基于真实代码 |
| Storage详解   | v1.0 | 2024-12-27 | 基于真实代码 |
| Detection详解 | v1.0 | 2024-12-27 | 基于真实代码 |
| Gateway详解   | v1.0 | 2024-12-27 | 基于真实代码 |
| 测试指南        | v1.0 | 2024-12-27 | 完整测试用例 |
| 代码示例        | v1.0 | 2024-12-27 | 完整案例   |

---

## 🔍 快速查找

### 按主题查找

#### 部署相关

- **快速部署**: [快速开始指南](./LogX-Quick-Start.md)
- **中间件配置**: [docker-compose.yml](../docker-compose.yml)
- **数据库初始化**: [init.sql](../scripts/init.sql)
- **详细配置**: [配置详解](./LogX-Configuration-Guide.md)

#### SDK 集成

- **Spring Boot**: [代码示例 - Spring Boot集成](./LogX-Code-Examples.md#spring-boot-集成)
- **纯 Java**: [代码示例 - 纯Java集成](./LogX-Code-Examples.md#纯java集成)
- **依赖管理**: [依赖关系](./LogX-Dependencies.md)
- **完整API**: [SDK详解](./LogX-SDK-Guide.md)
- **高级用例**: [代码示例 - 高级用例](./LogX-Code-Examples.md#高级用例)

#### 日志处理

- **处理流程**: [Engine详解 - 日志处理流程](./LogX-Engine-Guide.md#日志处理流程)
- **数据脱敏**: [Engine详解 - 数据脱敏](./LogX-Engine-Guide.md#数据脱敏)
- **批量写入**: [Engine详解 - Elasticsearch写入](./LogX-Engine-Guide.md#elasticsearch-写入)
- **性能优化**: [Engine详解 - 性能优化](./LogX-Engine-Guide.md#性能优化)

#### 存储归档

- **生命周期**: [Storage详解 - 数据生命周期](./LogX-Storage-Guide.md#数据生命周期管理)
- **热冷策略**: [Storage详解 - 热冷策略](./LogX-Storage-Guide.md#热冷策略)
- **数据导出**: [Storage详解 - 数据导出归档](./LogX-Storage-Guide.md#数据导出归档)
- **定时任务**: [Storage详解 - 定时任务](./LogX-Storage-Guide.md#定时任务)

#### 监控告警

- **规则引擎**: [Detection详解 - 规则引擎](./LogX-Detection-Guide.md#规则引擎)
- **告警配置**: [Detection详解 - 规则配置](./LogX-Detection-Guide.md#规则配置)
- **通知服务**: [Detection详解 - 通知服务](./LogX-Detection-Guide.md#通知服务)
- **最佳实践**: [Detection详解 - 最佳实践](./LogX-Detection-Guide.md#最佳实践)

#### 网关接入

- **HTTP网关**: [Gateway详解 - HTTP网关](./LogX-Gateway-Guide.md#http网关)
- **gRPC网关**: [Gateway详解 - gRPC网关](./LogX-Gateway-Guide.md#grpc网关)
- **限流机制**: [Gateway详解 - 限流机制](./LogX-Gateway-Guide.md#限流机制)
- **认证授权**: [Gateway详解 - 认证授权](./LogX-Gateway-Guide.md#认证授权)
- **性能对比**: [Gateway详解 - 性能对比](./LogX-Gateway-Guide.md#性能对比)

#### 测试相关

- **Struct测试**: [测试指南 - Protobuf Struct测试](./LogX-Testing-Guide.md#protobuf-struct测试)
- **规则测试**: [测试指南 - 规则检测测试](./LogX-Testing-Guide.md#规则检测测试)
- **端到端测试**: [测试指南 - 端到端测试](./LogX-Testing-Guide.md#端到端测试)
- **性能测试**: [测试指南 - 性能测试](./LogX-Testing-Guide.md#性能测试)

#### 运维管理

- **监控告警**: [配置详解 - 监控运维](./LogX-Configuration-Guide.md#监控与运维)
- **性能优化**: [配置详解 - 性能优化](./LogX-Configuration-Guide.md#性能优化建议)
- **故障排查**: [快速开始 - 故障排查](./LogX-Quick-Start.md#故障排查)

#### 开发指南

- **代码结构**: [项目结构](./LogX-Project-Structure.md)
- **模块依赖**: [依赖关系](./LogX-Dependencies.md)
- **gRPC 协议**: [配置详解 - gRPC说明](./LogX-Configuration-Guide.md#grpc-协议说明)

---

## ⚡ 常用命令

### 中间件管理

```bash
# 启动所有中间件
docker-compose up -d

# 查看状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 停止所有中间件
docker-compose down

# 清理数据重新开始
docker-compose down -v
```

### 应用管理

```bash
# 编译项目
mvn clean package -DskipTests

# 单体模式
java -jar logx-standalone/target/logx-standalone-0.0.1-SNAPSHOT.jar

# 微服务模式
./scripts/start-all.sh

# 停止服务
./scripts/stop-all.sh
```

### 健康检查

```bash
# 检查应用状态
curl http://localhost:8080/actuator/health

# 查看API文档
open http://localhost:8083/doc.html

# 查看Kibana
open http://localhost:5601

# 查看MinIO
open http://localhost:9001
```

---

## 🆘 获取帮助

### 问题排查流程

1. **查看文档**: 首先在本文档索引中查找相关主题
2. **检查日志**:
    - 应用日志: `logs/*.log`
    - 容器日志: `docker-compose logs [服务名]`
3. **运行健康检查**: `./scripts/health-check.sh`
4. **查看常见问题**: 各文档的故障排查章节

### 联系方式

- 📧 Email: support@example.com
- 💬 Issues: GitHub Issues
- 📖 Wiki: 项目 Wiki
- 👥 社区: Discussions

---

## 📝 文档贡献

欢迎改进文档！

### 贡献方式

1. Fork 项目
2. 编辑文档
3. 提交 Pull Request

### 文档规范

- 使用 Markdown 格式
- 保持简洁清晰
- 提供代码示例
- 添加截图说明

---

## 🎯 下一步行动

根据你的角色选择：

### 我是新手

→ 直接阅读 [快速开始指南](./LogX-Quick-Start.md)

### 我是开发者

→ 先看 [项目结构](./LogX-Project-Structure.md)，再看 [依赖关系](./LogX-Dependencies.md)

### 我是运维

→ 重点阅读 [配置详解](./LogX-Configuration-Guide.md)

### 我是架构师

→ 查看 [架构图](../scripts/logx-architecture.mermaid) 和完整的 [项目说明](./LogX-README-v2.md)

---

**祝你使用愉快！🎉**

最后更新: 2025-12-27
