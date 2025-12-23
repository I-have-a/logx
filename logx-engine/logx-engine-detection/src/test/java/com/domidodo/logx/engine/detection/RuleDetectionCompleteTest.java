package com.domidodo.logx.engine.detection;

import com.domidodo.logx.engine.detection.alerts.AlertService;
import com.domidodo.logx.engine.detection.alerts.NotificationService;
import com.domidodo.logx.engine.detection.entity.Alert;
import com.domidodo.logx.engine.detection.entity.Rule;
import com.domidodo.logx.engine.detection.mapper.AlertMapper;
import com.domidodo.logx.engine.detection.mapper.RuleMapper;
import com.domidodo.logx.engine.detection.rules.EnhancedRuleEngine;
import com.domidodo.logx.engine.detection.rules.RuleStateManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 异常检测模块完整测试
 * 演示各种规则类型和业务场景
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RuleDetectionCompleteTest {

    @Autowired
    private EnhancedRuleEngine ruleEngine;

    @Autowired
    private RuleStateManager stateManager;

    @Autowired
    private AlertService alertService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RuleMapper ruleMapper;

    @Autowired
    private AlertMapper alertMapper;

    @BeforeAll
    public static void setup() {
        System.out.println("========== 异常检测测试开始 ==========");
    }

    @AfterAll
    public static void teardown() {
        System.out.println("========== 异常检测测试结束 ==========");
    }

    /**
     * 测试1：字段值比较 - 数字字段
     */
    @Test
    @Order(1)
    public void test01_FieldCompare_Number() {
        System.out.println("\n--- 测试1：数字字段比较 ---");

        Rule rule = createRule(
                "响应时间过长",
                "FIELD_COMPARE",
                "module:订单管理",
                "responseTime",
                ">",
                "3000"
        );

        // 正常响应时间（不触发）
        Map<String, Object> normalLog = createLogData("订单管理", 1500L);
        boolean matched1 = ruleEngine.evaluate(rule, normalLog);
        Assertions.assertFalse(matched1, "正常响应时间不应触发告警");
        System.out.println("✅ 正常响应时间: 1500ms < 3000ms (不触发)");

        // 超长响应时间（触发）
        Map<String, Object> slowLog = createLogData("订单管理", 5000L);
        boolean matched2 = ruleEngine.evaluate(rule, slowLog);
        Assertions.assertTrue(matched2, "超长响应时间应触发告警");
        System.out.println("✅ 超长响应时间: 5000ms > 3000ms (触发告警)");

        System.out.println("✅ 数字字段比较测试完成");
    }

    /**
     * 测试2：字段值比较 - 字符串字段
     */
    @Test
    @Order(2)
    public void test02_FieldCompare_String() {
        System.out.println("\n--- 测试2：字符串字段比较 ---");

        Rule rule = createRule(
                "ERROR日志监控",
                "FIELD_COMPARE",
                "",
                "level",
                "=",
                "ERROR"
        );

        // INFO级别（不触发）
        Map<String, Object> infoLog = createLogData("订单管理", 100L);
        infoLog.put("level", "INFO");
        boolean matched1 = ruleEngine.evaluate(rule, infoLog);
        Assertions.assertFalse(matched1);
        System.out.println("✅ INFO级别日志不触发");

        // ERROR级别（触发）
        Map<String, Object> errorLog = createLogData("订单管理", 100L);
        errorLog.put("level", "ERROR");
        boolean matched2 = ruleEngine.evaluate(rule, errorLog);
        Assertions.assertTrue(matched2);
        System.out.println("✅ ERROR级别日志触发告警");

        // 测试contains操作符
        Rule containsRule = createRule(
                "空指针异常监控",
                "FIELD_COMPARE",
                "",
                "exception",
                "contains",
                "NullPointerException"
        );

        Map<String, Object> exceptionLog = createLogData("订单管理", 100L);
        exceptionLog.put("exception", "java.lang.NullPointerException: Cannot invoke method");
        boolean matched3 = ruleEngine.evaluate(containsRule, exceptionLog);
        Assertions.assertTrue(matched3);
        System.out.println("✅ 异常信息包含检测触发告警");

        System.out.println("✅ 字符串字段比较测试完成");
    }

    /**
     * 测试3：批量操作监控 - 用户维度
     */
    @Test
    @Order(3)
    public void test03_BatchOperation_User() {
        System.out.println("\n--- 测试3：批量操作监控（用户维度）---");

        Rule rule = createRule(
                "用户高频操作",
                "BATCH_OPERATION",
                "userId:",
                "operationCount",
                ">",
                "10:60" // 1分钟内超过10次
        );

        String userId = "user-" + UUID.randomUUID().toString();

        // 模拟用户短时间内多次操作
        int operationCount = 15;
        for (int i = 0; i < operationCount; i++) {
            Map<String, Object> log = createLogData("订单管理", 100L);
            log.put("userId", userId);
            log.put("operation", "创建订单");

            boolean matched = ruleEngine.evaluate(rule, log);

            if (i < 10) {
                Assertions.assertFalse(matched, "前10次不应触发");
            } else {
                Assertions.assertTrue(matched, "超过10次应触发");
                if (i == 10) {
                    System.out.println("✅ 第" + (i + 1) + "次操作触发告警");
                }
            }
        }

        System.out.println("✅ 用户批量操作监控测试完成");
        System.out.println("   共执行: " + operationCount + " 次操作");
        System.out.println("   阈值: 10次/60秒");
    }

    /**
     * 测试4：批量操作监控 - IP维度
     */
    @Test
    @Order(4)
    public void test04_BatchOperation_IP() {
        System.out.println("\n--- 测试4：批量操作监控（IP维度）---");

        Rule rule = createRule(
                "IP高频访问",
                "BATCH_OPERATION",
                "ip:",
                "operationCount",
                ">",
                "50:30" // 30秒内超过50次
        );

        String ip = "192.168.1." + new Random().nextInt(255);

        // 模拟同一IP大量请求
        int requestCount = 60;
        int triggeredCount = 0;

        for (int i = 0; i < requestCount; i++) {
            Map<String, Object> log = createLogData("API网关", 50L);
            log.put("ip", ip);

            boolean matched = ruleEngine.evaluate(rule, log);
            if (matched) {
                triggeredCount++;
            }
        }

        Assertions.assertTrue(triggeredCount > 0, "应触发告警");
        System.out.println("✅ IP高频访问监控测试完成");
        System.out.println("   共请求: " + requestCount + " 次");
        System.out.println("   触发次数: " + triggeredCount);
    }

    /**
     * 测试5：批量操作监控 - 模块维度
     */
    @Test
    @Order(5)
    public void test05_BatchOperation_Module() {
        System.out.println("\n--- 测试5：批量操作监控（模块维度）---");

        Rule rule = createRule(
                "订单模块调用激增",
                "BATCH_OPERATION",
                "module:订单管理",
                "operationCount",
                ">",
                "100:60" // 1分钟内超过100次
        );

        // 模拟模块调用量激增
        int callCount = 120;
        int triggeredCount = 0;

        for (int i = 0; i < callCount; i++) {
            Map<String, Object> log = createLogData("订单管理", 100L);
            log.put("operation", "查询订单");

            boolean matched = ruleEngine.evaluate(rule, log);
            if (matched) {
                triggeredCount++;
            }
        }

        Assertions.assertTrue(triggeredCount > 0);
        System.out.println("✅ 模块调用激增监控测试完成");
        System.out.println("   总调用: " + callCount + " 次");
        System.out.println("   触发次数: " + triggeredCount);
    }

    /**
     * 测试6：连续请求监控 - 接口连续失败
     */
    @Test
    @Order(6)
    public void test06_ContinuousRequest_API() {
        System.out.println("\n--- 测试6：接口连续失败监控 ---");

        Rule rule = createRule(
                "订单创建连续失败",
                "CONTINUOUS_REQUEST",
                "/api/order/create",
                "continuousFailure",
                ">",
                "5" // 连续5次失败
        );

        String apiUrl = "/api/order/create";

        // 模拟连续失败
        System.out.println("模拟连续失败场景：");
        for (int i = 0; i < 8; i++) {
            Map<String, Object> log = createLogData("订单管理", 100L);
            log.put("requestUrl", apiUrl);
            log.put("level", "ERROR");
            log.put("statusCode", 500);

            boolean matched = ruleEngine.evaluate(rule, log);

            System.out.println("   第" + (i + 1) + "次失败: " + (matched ? "触发告警 ⚠️" : "未触发"));

            if (i < 5) {
                Assertions.assertFalse(matched);
            } else {
                Assertions.assertTrue(matched);
            }
        }

        // 一次成功后，计数器应重置
        System.out.println("\n模拟恢复场景：");
        Map<String, Object> successLog = createLogData("订单管理", 100L);
        successLog.put("requestUrl", apiUrl);
        successLog.put("level", "INFO");
        successLog.put("statusCode", 200);
        boolean matched = ruleEngine.evaluate(rule, successLog);
        Assertions.assertFalse(matched);
        System.out.println("   接口恢复正常，计数器重置 ✅");

        // 再次失败，从1开始计数
        Map<String, Object> failLog = createLogData("订单管理", 100L);
        failLog.put("requestUrl", apiUrl);
        failLog.put("level", "ERROR");
        boolean matched2 = ruleEngine.evaluate(rule, failLog);
        Assertions.assertFalse(matched2);
        System.out.println("   新的失败从1开始计数（未触发）");

        System.out.println("✅ 连续失败监控测试完成");
    }

    /**
     * 测试7：连续请求监控 - 模块连续异常
     */
    @Test
    @Order(7)
    public void test07_ContinuousRequest_Module() {
        System.out.println("\n--- 测试7：模块连续异常监控 ---");

        Rule rule = createRule(
                "库存模块连续异常",
                "CONTINUOUS_REQUEST",
                "module:库存管理",
                "continuousFailure",
                ">",
                "3"
        );

        // 连续异常
        for (int i = 0; i < 5; i++) {
            Map<String, Object> log = createLogData("库存管理", 100L);
            log.put("level", "ERROR");

            boolean matched = ruleEngine.evaluate(rule, log);
            if (i >= 3) {
                Assertions.assertTrue(matched);
            }
        }

        System.out.println("✅ 模块连续异常监控测试完成");
    }

    /**
     * 测试8：业务场景 - 防刷单
     */
    @Test
    @Order(8)
    public void test08_Business_AntiFlood() {
        System.out.println("\n--- 测试8：业务场景 - 防刷单 ---");

        // 规则1：用户高频下单
        Rule rule1 = createRule(
                "用户高频下单",
                "BATCH_OPERATION",
                "userId:",
                "operationCount",
                ">",
                "5:300" // 5分钟内超过5次
        );

        // 规则2：订单金额异常
        Rule rule2 = createRule(
                "订单金额异常",
                "FIELD_COMPARE",
                "operation:创建订单",
                "amount",
                ">",
                "50000" // 超过5万
        );

        String suspiciousUserId = "user-suspicious-001";

        // 场景1：正常用户正常下单
        System.out.println("\n场景1：正常用户正常下单");
        Map<String, Object> normalOrder = createLogData("订单管理", 200L);
        normalOrder.put("userId", "user-normal-001");
        normalOrder.put("operation", "创建订单");
        normalOrder.put("amount", 299.99);

        boolean matched1 = ruleEngine.evaluate(rule1, normalOrder);
        boolean matched2 = ruleEngine.evaluate(rule2, normalOrder);
        Assertions.assertFalse(matched1 || matched2);
        System.out.println("   ✅ 正常订单，未触发任何告警");

        // 场景2：可疑用户频繁下单
        System.out.println("\n场景2：可疑用户频繁下单");
        for (int i = 0; i < 8; i++) {
            Map<String, Object> suspiciousOrder = createLogData("订单管理", 200L);
            suspiciousOrder.put("userId", suspiciousUserId);
            suspiciousOrder.put("operation", "创建订单");
            suspiciousOrder.put("amount", 99.99);

            boolean matched = ruleEngine.evaluate(rule1, suspiciousOrder);
            if (i >= 5) {
                Assertions.assertTrue(matched);
                if (i == 5) {
                    System.out.println("   ⚠️ 检测到刷单行为！");
                }
            }
        }

        // 场景3：大额订单
        System.out.println("\n场景3：大额可疑订单");
        Map<String, Object> largeOrder = createLogData("订单管理", 200L);
        largeOrder.put("userId", "user-whale-001");
        largeOrder.put("operation", "创建订单");
        largeOrder.put("amount", 88888.0);

        boolean matched3 = ruleEngine.evaluate(rule2, largeOrder);
        Assertions.assertTrue(matched3);
        System.out.println("   ⚠️ 检测到大额订单，需要人工审核");

        System.out.println("✅ 防刷单场景测试完成");
    }

    /**
     * 测试9：业务场景 - API网关防攻击
     */
    @Test
    @Order(9)
    public void test09_Business_APIGateway() {
        System.out.println("\n--- 测试9：业务场景 - API网关防攻击 ---");

        // 规则1：单IP高频访问
        Rule rule1 = createRule(
                "IP高频访问",
                "BATCH_OPERATION",
                "ip:",
                "operationCount",
                ">",
                "100:60" // 1分钟100次
        );

        // 规则2：404错误激增
        Rule rule2 = createRule(
                "404错误监控",
                "FIELD_COMPARE",
                "",
                "statusCode",
                "=",
                "404"
        );

        String attackIp = "203.0.113.123";

        // 模拟攻击场景
        System.out.println("模拟攻击场景：");
        int attackCount = 150;
        int triggered404 = 0;
        int triggeredFrequent = 0;

        for (int i = 0; i < attackCount; i++) {
            Map<String, Object> log = createLogData("API网关", 50L);
            log.put("ip", attackIp);
            log.put("statusCode", 404);
            log.put("requestUrl", "/api/admin/sensitive-data");

            boolean matched1 = ruleEngine.evaluate(rule1, log);
            boolean matched2 = ruleEngine.evaluate(rule2, log);

            if (matched1) triggeredFrequent++;
            if (matched2) triggered404++;
        }

        System.out.println("   共发起攻击: " + attackCount + " 次");
        System.out.println("   ⚠️ IP高频告警触发: " + triggeredFrequent + " 次");
        System.out.println("   ⚠️ 404错误告警触发: " + triggered404 + " 次");

        Assertions.assertTrue(triggeredFrequent > 0);
        Assertions.assertTrue(triggered404 > 0);

        System.out.println("✅ API网关防攻击场景测试完成");
    }

    /**
     * 测试10：业务场景 - 核心接口稳定性监控
     */
    @Test
    @Order(10)
    public void test10_Business_CoreAPIStability() {
        System.out.println("\n--- 测试10：核心接口稳定性监控 ---");

        // 规则1：支付接口连续失败
        Rule rule1 = createRule(
                "支付接口连续失败",
                "CONTINUOUS_REQUEST",
                "/api/payment/pay",
                "continuousFailure",
                ">",
                "3"
        );

        // 规则2：支付响应时间过长
        Rule rule2 = createRule(
                "支付响应过慢",
                "FIELD_COMPARE",
                "/api/payment/pay",
                "responseTime",
                ">",
                "5000"
        );

        String paymentApi = "/api/payment/pay";

        // 场景1：接口正常
        System.out.println("\n阶段1：接口正常运行");
        for (int i = 0; i < 3; i++) {
            Map<String, Object> log = createLogData("支付模块", 1500L);
            log.put("requestUrl", paymentApi);
            log.put("level", "INFO");

            boolean matched1 = ruleEngine.evaluate(rule1, log);
            boolean matched2 = ruleEngine.evaluate(rule2, log);
            Assertions.assertFalse(matched1 || matched2);
        }
        System.out.println("   ✅ 接口正常");

        // 场景2：开始出现慢响应
        System.out.println("\n阶段2：性能下降");
        Map<String, Object> slowLog = createLogData("支付模块", 8000L);
        slowLog.put("requestUrl", paymentApi);
        boolean matched = ruleEngine.evaluate(rule2, slowLog);
        Assertions.assertTrue(matched);
        System.out.println("   ⚠️ 响应时间过长告警");

        // 场景3：连续失败
        System.out.println("\n阶段3：接口故障");
        for (int i = 0; i < 5; i++) {
            Map<String, Object> failLog = createLogData("支付模块", 100L);
            failLog.put("requestUrl", paymentApi);
            failLog.put("level", "ERROR");
            failLog.put("statusCode", 500);

            boolean matched1 = ruleEngine.evaluate(rule1, failLog);
            if (i >= 3) {
                Assertions.assertTrue(matched1);
                if (i == 3) {
                    System.out.println("   🚨 连续失败告警！立即通知！");
                }
            }
        }

        System.out.println("✅ 核心接口稳定性监控测试完成");
    }

    /**
     * 测试11：告警服务集成测试
     */
    @Test
    @Order(11)
    public void test11_AlertService_Integration() {
        System.out.println("\n--- 测试11：告警服务集成测试 ---");

        Rule rule = createRule(
                "测试告警规则",
                "FIELD_COMPARE",
                "",
                "level",
                "=",
                "ERROR"
        );
        rule.setAlertLevel("CRITICAL");

        // 保存规则
        ruleMapper.insert(rule);
        System.out.println("✅ 规则已保存，ID: " + rule.getId());

        // 触发告警
        Map<String, Object> log = createLogData("测试模块", 100L);
        log.put("level", "ERROR");
        log.put("message", "这是一条测试错误日志");

        alertService.triggerAlert(rule, log);
        System.out.println("✅ 告警已触发");

        // 等待异步处理
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 查询告警记录
        List<Alert> alerts = alertMapper.selectPendingAlerts(rule.getTenantId());
        Assertions.assertFalse(alerts.isEmpty(), "应该有待处理的告警");
        System.out.println("✅ 查询到告警记录: " + alerts.size() + " 条");

        Alert alert = alerts.get(0);
        System.out.println("   告警ID: " + alert.getId());
        System.out.println("   告警级别: " + alert.getAlertLevel());
        System.out.println("   告警状态: " + alert.getStatus());

        System.out.println("✅ 告警服务集成测试完成");
    }

    /**
     * 测试12：通知服务测试
     */
    @Test
    @Order(12)
    public void test12_NotificationService() {
        System.out.println("\n--- 测试12：通知服务测试 ---");

        Alert alert = new Alert();
        alert.setId(999L);
        alert.setTenantId("tenant_test");
        alert.setSystemId("system_test");
        alert.setAlertLevel("CRITICAL");
        alert.setAlertType("FIELD_COMPARE");
        alert.setAlertContent("测试告警内容");
        alert.setTriggerTime(LocalDateTime.now());
        alert.setStatus("PENDING");

        // 测试立即通知
        System.out.println("测试严重告警立即通知：");
        notificationService.sendImmediate(alert);
        System.out.println("✅ 立即通知已发送");

        // 测试队列通知
        alert.setAlertLevel("WARNING");
        System.out.println("\n测试警告告警队列通知：");
        notificationService.addToQueue(alert);
        System.out.println("✅ 已加入通知队列");
        System.out.println("   当前队列大小: " + notificationService.getQueueSize());

        // 添加多个告警
        for (int i = 0; i < 5; i++) {
            Alert queueAlert = new Alert();
            queueAlert.setTenantId("tenant_test");
            queueAlert.setAlertLevel("WARNING");
            queueAlert.setAlertContent("批量测试告警 #" + i);
            notificationService.addToQueue(queueAlert);
        }
        System.out.println("   批量添加后队列大小: " + notificationService.getQueueSize());

        System.out.println("✅ 通知服务测试完成");
    }

    /**
     * 测试13：状态管理器测试
     */
    @Test
    @Order(13)
    public void test13_StateManager() {
        System.out.println("\n--- 测试13：状态管理器测试 ---");

        String testKey = "test:state:key:" + UUID.randomUUID();

        // 测试连续状态
        System.out.println("测试连续状态管理：");
        for (int i = 0; i < 5; i++) {
            int count = stateManager.recordContinuousFailure(testKey, true);
            System.out.println("   第" + (i + 1) + "次失败，累计: " + count);
            Assertions.assertEquals(i + 1, count);
        }

        // 成功后重置
        int count = stateManager.recordContinuousFailure(testKey, false);
        Assertions.assertEquals(0, count);
        System.out.println("   ✅ 成功后计数重置为: " + count);

        // 测试批量操作状态
        System.out.println("\n测试批量操作状态管理：");
        String batchKey = "test:batch:key:" + UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            int operCount = stateManager.recordBatchOperation(batchKey, 60);
            System.out.println("   第" + (i + 1) + "次操作，窗口内累计: " + operCount);
        }

        System.out.println("✅ 状态管理器测试完成");
    }

    /**
     * 测试14：性能测试
     */
    @Test
    @Order(14)
    public void test14_Performance() {
        System.out.println("\n--- 测试14：性能测试 ---");

        Rule rule = createRule(
                "性能测试规则",
                "FIELD_COMPARE",
                "",
                "responseTime",
                ">",
                "1000"
        );

        int testCount = 10000;
        long startTime = System.currentTimeMillis();

        int matchedCount = 0;
        for (int i = 0; i < testCount; i++) {
            Map<String, Object> log = createLogData("性能测试", 1500L);
            if (ruleEngine.evaluate(rule, log)) {
                matchedCount++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        double avgTime = duration / (double) testCount;
        double qps = 1000.0 / avgTime;

        System.out.println("✅ 性能测试完成");
        System.out.println("   总数: " + testCount + " 条");
        System.out.println("   匹配: " + matchedCount + " 条");
        System.out.println("   总耗时: " + duration + " ms");
        System.out.println("   平均: " + String.format("%.3f", avgTime) + " ms/条");
        System.out.println("   QPS: " + String.format("%.0f", qps * testCount));

        Assertions.assertTrue(qps > 1000, "QPS应该大于1000");
    }

    /**
     * 测试15：边界情况
     */
    @Test
    @Order(15)
    public void test15_EdgeCases() {
        System.out.println("\n--- 测试15：边界情况测试 ---");

        // 空日志数据
        Rule rule = createRule("测试", "FIELD_COMPARE", "", "responseTime", ">", "1000");
        boolean matched1 = ruleEngine.evaluate(rule, new HashMap<>());
        Assertions.assertFalse(matched1);
        System.out.println("✅ 空日志数据处理正常");

        // 字段不存在
        Map<String, Object> log = createLogData("测试", 100L);
        log.remove("responseTime");
        boolean matched2 = ruleEngine.evaluate(rule, log);
        Assertions.assertFalse(matched2);
        System.out.println("✅ 字段不存在处理正常");

        // null值
        log.put("responseTime", null);
        boolean matched3 = ruleEngine.evaluate(rule, log);
        Assertions.assertFalse(matched3);
        System.out.println("✅ null值处理正常");

        // 大数字
        log.put("responseTime", Long.MAX_VALUE);
        boolean matched4 = ruleEngine.evaluate(rule, log);
        Assertions.assertTrue(matched4);
        System.out.println("✅ 大数字处理正常");

        // 负数
        log.put("responseTime", -100L);
        boolean matched5 = ruleEngine.evaluate(rule, log);
        Assertions.assertFalse(matched5);
        System.out.println("✅ 负数处理正常");

        System.out.println("✅ 所有边界情况测试通过");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试规则
     */
    private Rule createRule(String name, String type, String target,
                            String metric, String operator, String value) {
        Rule rule = new Rule();
        rule.setTenantId("tenant_test");
        rule.setSystemId("system_test");
        rule.setRuleName(name);
        rule.setRuleType(type);
        rule.setMonitorTarget(target);
        rule.setMonitorMetric(metric);
        rule.setConditionOperator(operator);
        rule.setConditionValue(value);
        rule.setAlertLevel("WARNING");
        rule.setStatus(1);
        return rule;
    }

    /**
     * 创建测试日志数据
     */
    private Map<String, Object> createLogData(String module, Long responseTime) {
        Map<String, Object> log = new HashMap<>();
        log.put("tenantId", "tenant_test");
        log.put("systemId", "system_test");
        log.put("timestamp", System.currentTimeMillis());
        log.put("level", "INFO");
        log.put("module", module);
        log.put("responseTime", responseTime);
        log.put("userId", "user-test-001");
        log.put("userName", "测试用户");
        log.put("operation", "测试操作");
        log.put("requestUrl", "/api/test");
        log.put("ip", "192.168.1.100");
        return log;
    }
}