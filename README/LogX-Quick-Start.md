# LogX 快速开始指南 (5分钟上手)

## 🚀 最快部署路径

### 前置条件
- ✅ JDK 17+
- ✅ Maven 3.8+
- ✅ Docker & Docker Compose
- ✅ 8GB+ 可用内存

---

## 步骤 1: 克隆项目

```bash
git clone https://github.com/your-repo/LogX.git
cd LogX
```

---

## 步骤 2: 启动基础设施 (2分钟)

### 方式一: 使用提供的 docker-compose.yml

```bash
# 启动所有中间件
docker-compose up -d

# 查看状态
docker-compose ps
```

**预期输出**:
```
NAME            IMAGE                           STATUS
logx-mysql      mysql:8.0.44-debian            Up
logx-redis      redis:7.2-alpine               Up
logx-es         elasticsearch:7.17.15          Up
logx-kafka      apache/kafka:3.7.0             Up
logx-minio      minio/minio:latest             Up
logx-kibana     kibana:7.17.15                 Up (可选)
```

### 中间件访问信息

| 服务 | 地址 | 用户名 | 密码 |
|------|------|--------|------|
| MySQL | localhost:3307 | root | root123 |
| Redis | localhost:6379 | - | redis123 |
| Elasticsearch | localhost:9200 | elastic | 8rc3Jl1jlAK3uVZZyhF4 |
| Kafka | localhost:29092 | - | - |
| MinIO | localhost:9001 | admin | admin123 |
| Kibana | localhost:5601 | - | - |

---

## 步骤 3: 初始化数据库 (10秒)

```bash
# 等待MySQL完全启动 (约30秒)
sleep 30

# 执行初始化脚本
docker exec -i logx-mysql mysql -uroot -proot123 < scripts/init.sql

# 验证数据
docker exec -i logx-mysql mysql -uroot -proot123 -e "USE logx; SHOW TABLES;"
```

**预期输出**:
```
+----------------------------+
| Tables_in_logx            |
+----------------------------+
| log_alert_record          |
| log_exception_rule        |
| log_notification_config   |
| sys_system                |
| sys_tenant                |
+----------------------------+
```

---

## 步骤 4: 编译项目 (2分钟)

```bash
mvn clean package -DskipTests
```

**预期输出**:
```
[INFO] BUILD SUCCESS
[INFO] Total time: 02:15 min
```

---

## 步骤 5: 启动应用

### 方式一: 单体模式 (推荐新手)

```bash
cd logx-standalone
java -jar target/logx-standalone-0.0.1-SNAPSHOT.jar
```

**或者使用 Maven**:
```bash
cd logx-standalone
mvn spring-boot:run
```

### 方式二: 微服务模式

**使用脚本启动**:
```bash
# 赋予执行权限
chmod +x scripts/start-all.sh

# 一键启动
./scripts/start-all.sh
```

**手动启动** (需要5个终端):

```bash
# 终端1: HTTP网关
cd logx-gateway/logx-gateway-http
mvn spring-boot:run

# 终端2: 日志处理器
cd logx-engine/logx-engine-processor
mvn spring-boot:run

# 终端3: 异常检测
cd logx-engine/logx-engine-detection
mvn spring-boot:run

# 终端4: 存储管理
cd logx-engine/logx-engine-storage
mvn spring-boot:run

# 终端5: 管理控制台
cd logx-console/logx-console-api
mvn spring-boot:run
```

---

## 步骤 6: 验证部署

### 1. 检查服务状态

**单体模式**:
```bash
curl http://localhost:8080/actuator/health
```

**微服务模式**:
```bash
# HTTP网关
curl http://localhost:10240/api/v1/health

# 管理控制台
curl http://localhost:8083/api/monitor/health
```

**预期响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": "OK",
  "timestamp": 1766830714032
}
```

### 2. 访问 API 文档

打开浏览器访问:

**单体模式**:
- http://localhost:8080/doc.html

**微服务模式**:
- http://localhost:8083/doc.html

### 3. 查看 Kibana (可选)

- http://localhost:5601

---

## 步骤 7: 测试发送日志

### 创建测试客户端

新建 `TestLogX.java`:

```java
import com.domidodo.logx.sdk.core.LogXClient;

public class TestLogX {
    public static void main(String[] args) {
        // 1. 创建客户端
        LogXClient client = LogXClient.builder()
            .tenantId("company_a")
            .systemId("erp_system")
            .apiKey("sk_test_key_001")
            .gatewayUrl("http://localhost:10240")  // 或 8080 (单体模式)
            .build();
        
        // 2. 发送日志
        client.info("测试日志 - LogX部署成功!");
        client.warn("这是一条警告日志");
        
        try {
            int result = 10 / 0;
        } catch (Exception e) {
            client.error("发生异常", e);
        }
        
        // 3. 手动刷新缓冲区
        client.flush();
        
        // 4. 关闭客户端
        client.shutdown();
        
        System.out.println("✅ 日志发送成功!");
    }
}
```

### 运行测试

```bash
javac -cp logx-sdk-core-0.0.1-SNAPSHOT.jar TestLogX.java
java -cp .:logx-sdk-core-0.0.1-SNAPSHOT.jar TestLogX
```

---

## 步骤 8: 查询日志

### 方式一: 使用 API

```bash
curl -X POST http://localhost:8083/api/logs/query \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "company_a",
    "systemId": "erp_system",
    "startTime": "2024-12-27 00:00:00",
    "endTime": "2024-12-27 23:59:59",
    "page": 1,
    "size": 20
  }'
```

### 方式二: 使用 Kibana

1. 访问 http://localhost:5601
2. 进入 "Discover"
3. 创建索引模式: `logx-logs-*`
4. 查看日志数据

### 方式三: 直接查询 ES

```bash
# 查看所有索引
curl http://localhost:9200/_cat/indices?v

# 查询日志
curl -X GET "http://localhost:9200/logx-logs-*/_search?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "match_all": {}
    },
    "size": 10,
    "sort": [
      { "timestamp": "desc" }
    ]
  }'
```

---

## 故障排查

### 问题1: ES 启动失败

**现象**: `max virtual memory areas vm.max_map_count [65530] is too low`

**解决**:
```bash
# Linux
sudo sysctl -w vm.max_map_count=262144

# macOS
screen ~/Library/Containers/com.docker.docker/Data/vms/0/tty
sysctl -w vm.max_map_count=262144
```

### 问题2: Kafka 连接失败

**现象**: `Connection to node -1 could not be established`

**排查**:
```bash
# 检查Kafka状态
docker logs logx-kafka

# 测试连接
docker exec logx-kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 --list
```

### 问题3: MySQL 连接超时

**现象**: `Communications link failure`

**解决**:
```bash
# 检查MySQL状态
docker logs logx-mysql

# 等待MySQL完全启动
docker exec logx-mysql mysqladmin ping -h localhost -uroot -proot123
```

---

## 停止服务

### 停止应用

**单体模式**:
```bash
# Ctrl+C 停止进程
```

**微服务模式**:
```bash
# 使用脚本
./scripts/stop-all.sh

# 或手动查找并停止
ps aux | grep logx
kill <PID>
```

### 停止中间件

```bash
docker-compose down

# 如果需要清理数据
docker-compose down -v
```

---

## 下一步

### 学习路径

1. ✅ **入门**: 完成上述快速开始
2. 📚 **进阶**: 阅读完整配置文档
3. 🔧 **定制**: 修改配置适应业务需求
4. 🚀 **生产**: 性能优化和监控告警

### 推荐阅读

- [完整 README](./LogX-README-v2.md)
- [配置详解](./LogX-Configuration-Guide.md)
- [依赖关系](./LogX-Dependencies.md)
- [架构设计](./logx-architecture.mermaid)

---

## 常用命令速查

```bash
# 查看所有容器状态
docker-compose ps

# 查看容器日志
docker-compose logs -f [服务名]

# 重启某个服务
docker-compose restart [服务名]

# 进入容器
docker exec -it [容器名] bash

# 查看应用日志
tail -f logs/*.log

# 清理所有数据重新开始
docker-compose down -v
rm -rf logs/*
```

---

## 获取帮助

遇到问题? 

1. 检查日志: `docker-compose logs` 和 `logs/*.log`
2. 查看文档: 本项目的 README 和配置指南
3. 提交 Issue: GitHub Issues
4. 社区讨论: Discussions

**祝你使用愉快! 🎉**
