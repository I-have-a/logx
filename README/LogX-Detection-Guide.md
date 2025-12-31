# LogX Detection 模块技术文档

## 📑 目录

- [模块概述](#模块概述)
- [规则引擎](#规则引擎)
- [告警服务](#告警服务)
- [通知服务](#通知服务)
- [规则配置](#规则配置)
- [最佳实践](#最佳实践)

---

## 模块概述

### 核心功能

```
Detection模块负责：
├── 规则执行         # UpdatedRuleExecutor
├── 规则引擎         # EnhancedRuleEngine
├── 状态管理         # RuleStateManager
├── 告警生成         # AlertService
└── 通知发送         # NotificationService
```

### 数据流

```
Kafka(logx-logs-processing)
    ↓
RuleExecutor 拉取日志
    ↓
EnhancedRuleEngine 评估规则
    ↓
AlertService 创建告警
    ↓
NotificationService 发送通知
```

---

## 规则引擎

### 1. 规则类型

LogX支持5种核心规则类型：

| 规则类型 | 说明 | 使用场景 |
|---------|------|---------|
| **FIELD_COMPARE** | 字段值比较 | 监控任意字段值 |
| **BATCH_OPERATION** | 批量操作监控 | 检测异常批量操作 |
| **CONTINUOUS_REQUEST** | 连续请求监控 | 检测连续失败 |
| **RESPONSE_TIME** | 响应时间监控 | 慢请求告警 |
| **ERROR_RATE** | 错误率监控 | 错误日志告警 |

---

### 2. 字段值比较规则 (FIELD_COMPARE)

#### 功能说明

对日志中任意字段进行比较，支持数字和字符串比较。

#### 支持的运算符

**数字比较**:
- `>` 大于
- `>=` 大于等于
- `<` 小于
- `<=` 小于等于
- `=` 等于
- `!=` 不等于

**字符串比较**:
- `=` 等于
- `!=` 不等于
- `contains` 包含
- `startsWith` 以...开头
- `endsWith` 以...结尾
- `matches` 正则匹配

#### 配置示例

**示例1: 监控响应时间**
```sql
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_metric, condition_operator, condition_value,
    alert_level, status
) VALUES (
    'company_a', 'erp_system', '响应时间过长',
    'FIELD_COMPARE',
    'responseTime',  -- 监控字段
    '>',             -- 运算符
    '5000',          -- 阈值（5秒）
    'WARNING', 1
);
```

**示例2: 监控特定用户**
```sql
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_metric, condition_operator, condition_value,
    alert_level, status
) VALUES (
    'company_a', 'erp_system', 'VIP用户错误',
    'FIELD_COMPARE',
    'userId',     -- 监控字段
    '=',          -- 运算符
    'vip_user',   -- 值
    'CRITICAL', 1
);
```

**示例3: 监控日志级别**
```sql
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_metric, condition_operator, condition_value,
    alert_level, status
) VALUES (
    'company_a', 'erp_system', '严重错误',
    'FIELD_COMPARE',
    'level',    -- 监控字段
    '=',        -- 运算符
    'ERROR',    -- 值
    'CRITICAL', 1
);
```

#### 代码实现

```java
private boolean evaluateFieldCompare(Rule rule, Map<String, Object> logData) {
    String fieldName = rule.getMonitorMetric();      // 字段名
    String operator = rule.getConditionOperator();   // 运算符
    String expectedValue = rule.getConditionValue(); // 期望值
    
    Object actualValue = logData.get(fieldName);
    if (actualValue == null) {
        return false;
    }
    
    // 数字比较
    if (actualValue instanceof Number) {
        long actual = ((Number) actualValue).longValue();
        long expected = Long.parseLong(expectedValue);
        return compareNumber(actual, expected, operator);
    }
    
    // 字符串比较
    String actualStr = actualValue.toString();
    return compareString(actualStr, expectedValue, operator);
}
```

---

### 3. 批量操作监控规则 (BATCH_OPERATION)

#### 功能说明

监控时间窗口内的操作次数，检测异常批量操作。

#### 配置格式

```
conditionValue: {次数}:{时间窗口(秒)}
示例: "100:300" 表示 300秒内操作100次
```

#### 监控维度

支持多种监控维度：

| 维度 | monitorTarget格式 | 说明 |
|------|------------------|------|
| 用户 | `userId:12345` | 监控特定用户 |
| 模块 | `module:订单管理` | 监控特定模块 |
| IP | `ip:192.168.1.1` | 监控特定IP |
| 操作 | `operation:删除` | 监控特定操作 |

#### 配置示例

**示例1: 监控用户批量操作**
```sql
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_target, condition_operator, condition_value,
    alert_level, status
) VALUES (
    'company_a', 'erp_system', '用户批量删除告警',
    'BATCH_OPERATION',
    'userId:12345',  -- 监控用户12345
    '>',             -- 大于
    '100:300',       -- 300秒内超过100次
    'CRITICAL', 1
);
```

**示例2: 监控IP异常请求**
```sql
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_target, condition_operator, condition_value,
    alert_level, status
) VALUES (
    'company_a', 'erp_system', 'IP异常请求',
    'BATCH_OPERATION',
    'ip:192.168.1.100',  -- 监控特定IP
    '>',                 -- 大于
    '1000:60',           -- 1分钟内超过1000次
    'WARNING', 1
);
```

**示例3: 监控模块高频操作**
```sql
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_target, condition_operator, condition_value,
    alert_level, status
) VALUES (
    'company_a', 'erp_system', '订单模块高频操作',
    'BATCH_OPERATION',
    'module:订单管理',  -- 监控订单模块
    '>',              -- 大于
    '500:300',        -- 5分钟内超过500次
    'WARNING', 1
);
```

#### 代码实现

```java
private boolean evaluateBatchOperation(Rule rule, Map<String, Object> logData) {
    String target = rule.getMonitorTarget();
    String conditionValue = rule.getConditionValue();
    String operator = rule.getConditionOperator();
    
    // 解析条件值：次数:时间窗口(秒)
    String[] parts = conditionValue.split(":");
    int threshold = Integer.parseInt(parts[0]);        // 100
    int windowSeconds = Integer.parseInt(parts[1]);    // 300
    
    // 构建状态key（包含维度信息）
    String stateKey = buildBatchOperationKey(rule, logData, target);
    
    // 记录本次操作并获取时间窗口内的总次数
    int operationCount = stateManager.recordBatchOperation(stateKey, windowSeconds);
    
    // 比较操作次数
    return compareNumber(operationCount, threshold, operator);
}
```

#### 状态管理

```java
public int recordBatchOperation(String key, int windowSeconds) {
    BatchOperationState state = batchOperationMap.computeIfAbsent(
        key, k -> new BatchOperationState()
    );
    
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime windowStart = now.minusSeconds(windowSeconds);
    
    // 清理过期的时间戳
    state.timestamps.removeIf(time -> time.isBefore(windowStart));
    
    // 添加当前时间戳
    state.timestamps.add(now);
    
    return state.timestamps.size();
}
```

---

### 4. 连续请求监控规则 (CONTINUOUS_REQUEST)

#### 功能说明

监控连续成功/失败的次数，检测服务异常。

#### 监控指标

- `continuousFailure` - 连续失败次数
- `continuousSuccess` - 连续成功次数

#### 失败判断条件

1. 日志级别为 `ERROR`
2. HTTP状态码 >= 500

#### 配置示例

**示例1: 监控接口连续失败**
```sql
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_target, monitor_metric, 
    condition_operator, condition_value,
    alert_level, status
) VALUES (
    'company_a', 'erp_system', '订单接口连续失败',
    'CONTINUOUS_REQUEST',
    '/api/order/create',   -- 监控接口
    'continuousFailure',   -- 连续失败
    '>',                   -- 大于
    '5',                   -- 5次
    'CRITICAL', 1
);
```

**示例2: 监控模块连续失败**
```sql
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_target, monitor_metric,
    condition_operator, condition_value,
    alert_level, status
) VALUES (
    'company_a', 'erp_system', '支付模块连续失败',
    'CONTINUOUS_REQUEST',
    'module:支付管理',    -- 监控模块
    'continuousFailure',  -- 连续失败
    '>',                  -- 大于
    '3',                  -- 3次
    'CRITICAL', 1
);
```

#### 代码实现

```java
private boolean evaluateContinuousRequest(Rule rule, Map<String, Object> logData) {
    String target = rule.getMonitorTarget();
    String metric = rule.getMonitorMetric();
    int threshold = Integer.parseInt(rule.getConditionValue());
    
    // 构建状态key
    String stateKey = buildContinuousRequestKey(rule, logData, target);
    
    // 判断本次请求是否失败
    boolean isFailed = isRequestFailed(logData, metric);
    
    // 记录连续状态并获取计数
    int continuousCount = stateManager.recordContinuousFailure(stateKey, isFailed);
    
    // 只在失败时才触发告警判断
    if (isFailed) {
        return compareNumber(continuousCount, threshold, operator);
    }
    
    return false;
}

private boolean isRequestFailed(Map<String, Object> logData, String metric) {
    if ("continuousFailure".equals(metric)) {
        // 检查level是否为ERROR
        String level = (String) logData.get("level");
        if ("ERROR".equals(level)) {
            return true;
        }
        
        // 检查状态码是否为5xx
        Object statusCode = logData.get("statusCode");
        if (statusCode instanceof Number) {
            int code = ((Number) statusCode).intValue();
            return code >= 500;
        }
    }
    
    return false;
}
```

#### 状态管理

```java
public int recordContinuousFailure(String key, boolean isFailed) {
    ContinuousState state = continuousStateMap.computeIfAbsent(
        key, k -> new ContinuousState()
    );
    
    if (isFailed) {
        state.failureCount.incrementAndGet();
        state.lastFailureTime = LocalDateTime.now();
    } else {
        // 成功则重置计数
        state.failureCount.set(0);
    }
    
    return state.failureCount.get();
}
```

---

### 5. 响应时间监控 (RESPONSE_TIME)

#### 配置示例

```sql
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_target, condition_operator, condition_value,
    alert_level, status
) VALUES (
    'company_a', 'erp_system', '接口响应时间过长',
    'RESPONSE_TIME',
    '/api/order/list',  -- 监控接口
    '>',                -- 大于
    '3000',             -- 3秒
    'WARNING', 1
);
```

---

### 6. 错误率监控 (ERROR_RATE)

#### 配置示例

```sql
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_target, alert_level, status
) VALUES (
    'company_a', 'erp_system', '系统错误告警',
    'ERROR_RATE',
    'all',       -- 所有错误
    'ERROR', 1
);
```

---

## 告警服务

### 1. 告警流程

```
规则匹配
    ↓
创建Alert记录
    ↓
保存到数据库
    ↓
判断告警级别
    ↓
├── CRITICAL → 立即发送通知
└── WARNING/INFO → 加入队列（批量发送）
```

### 2. 告警级别

| 级别 | 说明 | 通知方式 | 示例 |
|------|------|---------|------|
| **CRITICAL** | 严重 | 立即发送 | 连续失败、系统崩溃 |
| **WARNING** | 警告 | 批量发送 | 响应时间过长 |
| **INFO** | 提示 | 批量发送 | 一般性提醒 |

### 3. 代码实现

```java
@Async
@Transactional
public void triggerAlert(Rule rule, Map<String, Object> logData) {
    // 1. 创建告警记录
    Alert alert = createAlert(rule, logData);
    
    // 2. 保存到数据库
    alertMapper.insert(alert);
    
    // 3. 发送通知
    AlertLevelEnum level = AlertLevelEnum.fromCode(rule.getAlertLevel());
    if (level.isImmediateNotify()) {
        // 严重告警立即发送
        notificationService.sendImmediate(alert);
    } else {
        // 其他告警加入队列，批量发送
        notificationService.addToQueue(alert);
    }
}
```

### 4. 告警内容生成

```java
public String generateAlertContent(Rule rule, Map<String, Object> logData) {
    StringBuilder content = new StringBuilder();
    
    content.append("规则名称: ").append(rule.getRuleName()).append("\n");
    content.append("规则类型: ").append(getRuleTypeDesc(rule.getRuleType())).append("\n");
    content.append("监控对象: ").append(rule.getMonitorTarget()).append("\n");
    content.append("触发条件: ").append(rule.getMonitorMetric())
           .append(" ").append(rule.getConditionOperator())
           .append(" ").append(rule.getConditionValue()).append("\n");
    
    content.append("\n触发日志详情:\n");
    content.append("时间: ").append(logData.get("timestamp")).append("\n");
    content.append("级别: ").append(logData.get("level")).append("\n");
    content.append("模块: ").append(logData.get("module")).append("\n");
    content.append("消息: ").append(logData.get("message")).append("\n");
    
    if (logData.containsKey("responseTime")) {
        content.append("响应时间: ").append(logData.get("responseTime")).append("ms\n");
    }
    
    return content.toString();
}
```

**生成的告警内容示例**:
```
规则名称: 订单接口连续失败
规则类型: 连续请求监控
监控对象: /api/order/create
触发条件: continuousFailure > 5

触发日志详情:
时间: 2024-12-27T10:30:00
级别: ERROR
模块: 订单管理
消息: 创建订单失败: 数据库连接超时
响应时间: 5000ms
用户: 张三 (user123)
```

---

## 通知服务

### 1. 通知策略

```
CRITICAL级别 → 立即发送（邮件 + 短信 + Webhook）
WARNING级别 → 加入队列，每小时批量发送
INFO级别 → 加入队列，每小时批量发送
```

### 2. 立即发送

```java
public void sendImmediate(Alert alert) {
    log.info("发送即时通知: id={}, level={}",
        alert.getId(), alert.getAlertLevel());
    
    // 并发发送多种通知
    CompletableFuture.allOf(
        CompletableFuture.runAsync(() -> sendEmail(alert)),
        CompletableFuture.runAsync(() -> sendSms(alert)),
        CompletableFuture.runAsync(() -> sendWebhook(alert))
    ).join();
}
```

### 3. 批量发送

```java
@Scheduled(cron = "0 0 * * * ?") // 每小时整点执行
public void sendBatchNotifications() {
    List<Alert> alerts = new ArrayList<>();
    pendingQueue.drainTo(alerts, 1000); // 最多取1000条
    
    if (alerts.isEmpty()) {
        return;
    }
    
    // 按租户分组
    Map<String, List<Alert>> groupedAlerts = groupByTenant(alerts);
    
    // 发送汇总通知
    for (Map.Entry<String, List<Alert>> entry : groupedAlerts.entrySet()) {
        sendSummaryNotification(entry.getKey(), entry.getValue());
    }
}
```

### 4. 汇总通知格式

```
【LogX告警汇总】

租户: company_a
告警数量: 150

严重: 5
警告: 120
提示: 25

详情请登录控制台查看。
```

### 5. 通知渠道

**支持的通知方式**:

| 渠道 | 用途 | 优先级 |
|------|------|-------|
| **邮件** | 详细告警信息 | 所有级别 |
| **短信** | 严重告警 | CRITICAL |
| **Webhook** | 企业IM（钉钉/企微/飞书） | CRITICAL |
| **站内消息** | 控制台通知 | 所有级别 |

**TODO实现**:
```java
// 1. 邮件发送（JavaMail）
private void sendEmail(Alert alert) {
    // TODO: 集成JavaMail
}

// 2. 短信发送（阿里云/腾讯云）
private void sendSms(Alert alert) {
    // TODO: 集成短信服务
}

// 3. Webhook（企业微信/钉钉/飞书）
private void sendWebhook(Alert alert) {
    // TODO: 实现Webhook调用
}
```

---

## 规则配置

### 1. 数据库表结构

```sql
CREATE TABLE log_exception_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    system_id VARCHAR(64) NOT NULL COMMENT '系统ID',
    rule_name VARCHAR(255) NOT NULL COMMENT '规则名称',
    rule_type VARCHAR(50) NOT NULL COMMENT '规则类型',
    monitor_target VARCHAR(255) COMMENT '监控对象',
    monitor_metric VARCHAR(100) COMMENT '监控指标',
    condition_operator VARCHAR(20) COMMENT '条件操作符',
    condition_value VARCHAR(255) COMMENT '条件值',
    alert_level VARCHAR(20) NOT NULL COMMENT '告警级别',
    status INT DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_system (tenant_id, system_id),
    INDEX idx_status (status)
) COMMENT='异常规则表';
```

### 2. 告警记录表

```sql
CREATE TABLE log_alert_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    system_id VARCHAR(64) NOT NULL,
    rule_id BIGINT NOT NULL COMMENT '规则ID',
    alert_level VARCHAR(20) NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    alert_content TEXT COMMENT '告警内容',
    trigger_time DATETIME NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RESOLVED',
    handle_user VARCHAR(64) COMMENT '处理人',
    handle_time DATETIME COMMENT '处理时间',
    handle_remark TEXT COMMENT '处理备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_trigger_time (trigger_time)
) COMMENT='告警记录表';
```

### 3. 规则示例数据

```sql
-- 1. 响应时间监控
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_target, condition_operator, condition_value,
    alert_level, status
) VALUES (
    'company_a', 'erp_system', '接口响应时间过长',
    'RESPONSE_TIME',
    '/api/order/create', '>', '3000',
    'WARNING', 1
);

-- 2. 连续失败监控
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_target, monitor_metric,
    condition_operator, condition_value,
    alert_level, status
) VALUES (
    'company_a', 'erp_system', '支付接口连续失败',
    'CONTINUOUS_REQUEST',
    '/api/payment/pay', 'continuousFailure',
    '>', '5',
    'CRITICAL', 1
);

-- 3. 批量操作监控
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_target, condition_operator, condition_value,
    alert_level, status
) VALUES (
    'company_a', 'erp_system', '用户批量删除',
    'BATCH_OPERATION',
    'operation:删除', '>', '100:300',
    'WARNING', 1
);

-- 4. 字段值比较
INSERT INTO log_exception_rule (
    tenant_id, system_id, rule_name, rule_type,
    monitor_metric, condition_operator, condition_value,
    alert_level, status
) VALUES (
    'company_a', 'erp_system', '错误日志告警',
    'FIELD_COMPARE',
    'level', '=', 'ERROR',
    'ERROR', 1
);
```

---

## 最佳实践

### 1. 规则设计原则

**✅ 好的规则**:
```sql
-- 明确的监控对象
monitor_target: '/api/order/create'

-- 合理的阈值
condition_value: '3000'  -- 3秒响应时间

-- 适当的告警级别
alert_level: 'WARNING'  -- 不是所有问题都是CRITICAL
```

**❌ 避免的做法**:
```sql
-- 过于宽泛的监控
monitor_target: 'all'

-- 过低的阈值（产生大量告警）
condition_value: '100'  -- 响应时间>100ms就告警

-- 滥用CRITICAL级别
alert_level: 'CRITICAL'  -- 所有规则都设为严重
```

### 2. 阈值设置建议

| 场景 | 建议阈值 | 说明 |
|------|---------|------|
| API响应时间 | 3000ms | 超过3秒告警 |
| 连续失败 | 5次 | 连续5次失败告警 |
| 批量操作 | 100次/5分钟 | 5分钟内超过100次 |
| 错误率 | 5% | 错误率超过5% |

### 3. 告警级别使用

```
CRITICAL - 严重影响业务的问题
├── 连续失败 > 5次
├── 数据库连接失败
├── 支付接口异常
└── 核心服务宕机

WARNING - 需要关注但不紧急
├── 响应时间 > 3秒
├── 批量操作异常
├── 缓存失败（有降级）
└── 非核心功能异常

INFO - 一般性提醒
├── 配置变更
├── 定时任务执行
└── 审计日志
```

### 4. 规则维护

**定期审查**:
```java
// 1. 查询高频触发的规则
@Select("""
    SELECT rule_name, trigger_count
    FROM log_exception_rule
    WHERE trigger_count > 100
    ORDER BY trigger_count DESC
""")
List<Map<String, Object>> getHighTriggeredRules();

// 2. 禁用长期未触发的规则
@Select("""
    UPDATE log_exception_rule
    SET status = 0
    WHERE last_trigger_time < DATE_SUB(NOW(), INTERVAL 30 DAY)
""")
void disableInactiveRules();
```

### 5. 性能优化

**规则缓存**:
```java
// 缓存规则，避免每次都查数据库
private final Map<String, List<Rule>> ruleCache = new ConcurrentHashMap<>();

@Scheduled(fixedRate = 60000) // 每分钟刷新
public void refreshRules() {
    loadRules();
}
```

**状态清理**:
```java
@Scheduled(fixedRate = 300000) // 每5分钟清理
public void cleanupExpiredStates() {
    stateManager.cleanupExpiredStates();
}
```

---

## 监控指标

### 规则统计

```java
// 按类型统计规则
@Select("""
    SELECT rule_type, COUNT(*) as rule_count, SUM(trigger_count) as total_triggers
    FROM log_exception_rule
    WHERE status = 1
    GROUP BY rule_type
""")
List<Map<String, Object>> countAlertsByRuleType();
```

**示例结果**:
```
规则类型             规则数  触发次数
FIELD_COMPARE       15      2,345
BATCH_OPERATION     8       567
CONTINUOUS_REQUEST  10      1,234
RESPONSE_TIME       12      3,456
ERROR_RATE          5       890
```

### 告警统计

```java
// 统计告警数量
public long countAlerts(String tenantId, LocalDateTime start, LocalDateTime end) {
    return alertMapper.countAlerts(tenantId, start, end);
}
```

---

## 故障排查

### 1. 规则不触发

**排查步骤**:
```bash
# 1. 检查规则状态
SELECT * FROM log_exception_rule WHERE id = 123;

# 2. 检查规则缓存
# 查看日志
tail -f logs/detection.log | grep "规则缓存"

# 3. 手动刷新规则
curl -X POST http://localhost:10252/api/rules/refresh
```

### 2. 告警过多

**排查**:
```sql
-- 查询频繁触发的规则
SELECT rule_name, trigger_count, last_trigger_time
FROM log_exception_rule
WHERE trigger_count > 100
  AND last_trigger_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
ORDER BY trigger_count DESC;
```

**解决**:
```sql
-- 调整阈值
UPDATE log_exception_rule
SET condition_value = '5000'  -- 放宽阈值
WHERE id = 123;

-- 或临时禁用
UPDATE log_exception_rule
SET status = 0
WHERE id = 123;
```

### 3. 通知未发送

**排查**:
```java
// 检查通知队列
int queueSize = notificationService.getQueueSize();
Map<String, Integer> counter = notificationService.getAlertCounter();

log.info("待发送: {}, 计数: {}", queueSize, counter);
```

---

## 下一步

- 查看 [Storage模块文档](./LogX-Storage-Guide.md) 了解数据归档
- 查看 [Engine模块文档](./LogX-Engine-Guide.md) 了解日志处理
- 查看 [代码示例](./LogX-Code-Examples.md) 学习实际用法
