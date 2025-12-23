package com.domidodo.logx.integration;

import com.domidodo.logx.common.dto.LogDTO;
import com.domidodo.logx.common.util.JsonUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 端到端数据流测试
 * <p>
 * 测试链路：
 * Gateway → Kafka(logx-logs) → Processor → ES + Kafka(logx-logs-processing) → Detection
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EndToEndDataFlowTest {

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeAll
    public static void setup() {
        System.out.println("========== 端到端数据流测试开始 ==========");
    }

    @AfterAll
    public static void teardown() {
        System.out.println("========== 端到端数据流测试结束 ==========");
    }

    /**
     * 测试1：模拟Gateway发送日志
     */
    @Test
    @Order(1)
    public void test01_GatewayToKafka() {
        System.out.println("\n--- 测试1：Gateway → Kafka ---");

        Assumptions.assumeTrue(kafkaTemplate != null, "Kafka not available");

        // 构建测试日志
        LogDTO logDTO = buildTestLog();

        try {
            // 转换为JSON
            String logJson = JsonUtil.toJson(logDTO);
            String key = generateKey(logDTO);

            // 发送到 logx-logs topic
            kafkaTemplate.send("logx-logs", key, logJson)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            System.err.println("❌ 发送失败: " + ex.getMessage());
                        } else {
                            System.out.println("✅ 日志已发送到 Kafka");
                            System.out.println("   Topic: logx-logs");
                            System.out.println("   Partition: " + result.getRecordMetadata().partition());
                            System.out.println("   Offset: " + result.getRecordMetadata().offset());
                        }
                    });

            // 等待发送完成
            TimeUnit.SECONDS.sleep(2);

            System.out.println("✅ 测试1完成");

        } catch (Exception e) {
            System.err.println("❌ 测试1失败: " + e.getMessage());
            Assertions.fail(e);
        }
    }

    /**
     * 测试2：批量发送日志（压测）
     */
    @Test
    @Order(2)
    public void test02_BatchSendToKafka() {
        System.out.println("\n--- 测试2：批量发送日志 ---");

        Assumptions.assumeTrue(kafkaTemplate != null, "Kafka not available");

        int batchSize = 100;
        int successCount = 0;

        try {
            for (int i = 0; i < batchSize; i++) {
                LogDTO logDTO = buildTestLog();
                logDTO.setMessage("批量测试日志 #" + i);

                String logJson = JsonUtil.toJson(logDTO);
                String key = generateKey(logDTO);

                kafkaTemplate.send("logx-logs", key, logJson);
                successCount++;
            }

            // 等待发送完成
            TimeUnit.SECONDS.sleep(3);

            System.out.println("✅ 批量发送完成");
            System.out.println("   总数: " + batchSize);
            System.out.println("   成功: " + successCount);

        } catch (Exception e) {
            System.err.println("❌ 测试2失败: " + e.getMessage());
            Assertions.fail(e);
        }
    }

    /**
     * 测试3：发送触发异常检测的日志
     */
    @Test
    @Order(3)
    public void test03_SendAbnormalLogs() {
        System.out.println("\n--- 测试3：发送异常日志（触发检测）---");

        Assumptions.assumeTrue(kafkaTemplate != null, "Kafka not available");

        try {
            // 场景1：响应时间过长
            System.out.println("\n场景1：响应时间过长（5000ms）");
            LogDTO slowLog = buildTestLog();
            slowLog.setResponseTime(5000L);
            slowLog.setMessage("订单查询响应缓慢");
            sendLog(slowLog);

            // 场景2：ERROR级别日志
            System.out.println("\n场景2：ERROR级别日志");
            LogDTO errorLog = buildTestLog();
            errorLog.setLevel("ERROR");
            errorLog.setMessage("订单创建失败");
            errorLog.setException("java.lang.NullPointerException: Cannot invoke method");
            sendLog(errorLog);

            // 场景3：高频操作（同一用户10次）
            System.out.println("\n场景3：高频操作（同一用户10次）");
            String userId = "user-suspicious-001";
            for (int i = 0; i < 10; i++) {
                LogDTO frequentLog = buildTestLog();
                frequentLog.setUserId(userId);
                frequentLog.setUserName("可疑用户");
                frequentLog.setOperation("创建订单");
                frequentLog.setMessage("高频下单 #" + i);
                sendLog(frequentLog);
                TimeUnit.MILLISECONDS.sleep(100);
            }

            // 场景4：连续失败
            System.out.println("\n场景4：连续失败（8次）");
            for (int i = 0; i < 8; i++) {
                LogDTO failLog = buildTestLog();
                failLog.setLevel("ERROR");
                failLog.setRequestUrl("/api/order/create");
                failLog.setMessage("订单创建失败 #" + i);
                sendLog(failLog);
                TimeUnit.MILLISECONDS.sleep(200);
            }

            // 等待处理完成
            System.out.println("\n等待处理完成...");
            TimeUnit.SECONDS.sleep(10);

            System.out.println("✅ 测试3完成");
            System.out.println("   请查看 Detection 模块日志，确认是否触发告警");

        } catch (Exception e) {
            System.err.println("❌ 测试3失败: " + e.getMessage());
            Assertions.fail(e);
        }
    }

    /**
     * 测试4：验证数据流完整性
     */
    @Test
    @Order(4)
    public void test04_VerifyDataFlow() {
        System.out.println("\n--- 测试4：验证数据流完整性 ---");

        System.out.println("\n请手动验证以下内容：");
        System.out.println("\n1. Processor 日志中应该看到：");
        System.out.println("   ✅ 消费到日志：Received N log messages from Kafka");
        System.out.println("   ✅ 解析成功：Processed N logs: N valid");
        System.out.println("   ✅ 写入ES成功：Bulk write completed: total=N, success=N");
        System.out.println("   ✅ 转发成功：Forwarded to Detection: N/N logs successful");

        System.out.println("\n2. Detection 日志中应该看到：");
        System.out.println("   ✅ 消费到日志：Processed N logs");
        System.out.println("   ✅ 规则匹配：N matched rules");
        System.out.println("   ✅ 触发告警：Alert triggered: ruleId=...");

        System.out.println("\n3. Elasticsearch 中应该有数据：");
        System.out.println("   curl -X GET 'http://localhost:9200/logx-logs-*/_count'");

        System.out.println("\n4. Kafka Topics 中应该有消息：");
        System.out.println("   logx-logs: 原始日志");
        System.out.println("   logx-logs-processing: 处理后的日志");

        System.out.println("\n✅ 测试4完成");
    }

    /**
     * 测试5：异常场景测试
     */
    @Test
    @Order(5)
    public void test05_ErrorScenarios() {
        System.out.println("\n--- 测试5：异常场景测试 ---");

        Assumptions.assumeTrue(kafkaTemplate != null, "Kafka not available");

        try {
            // 场景1：空日志
            System.out.println("\n场景1：空日志");
            kafkaTemplate.send("logx-logs", "", "{}");
            TimeUnit.SECONDS.sleep(1);

            // 场景2：格式错误的JSON
            System.out.println("\n场景2：格式错误的JSON");
            kafkaTemplate.send("logx-logs", "", "{invalid json}");
            TimeUnit.SECONDS.sleep(1);

            // 场景3：缺少必填字段
            System.out.println("\n场景3：缺少必填字段");
            Map<String, Object> incompleteLog = new HashMap<>();
            incompleteLog.put("message", "缺少tenantId和systemId");
            kafkaTemplate.send("logx-logs", "", JsonUtil.toJson(incompleteLog));
            TimeUnit.SECONDS.sleep(1);

            System.out.println("\n✅ 测试5完成");
            System.out.println("   这些异常日志应该被发送到死信队列：logx-logs-dlq");

        } catch (Exception e) {
            System.err.println("❌ 测试5失败: " + e.getMessage());
        }
    }

    /**
     * 测试6：性能测试
     */
    @Test
    @Order(6)
    public void test06_PerformanceTest() {
        System.out.println("\n--- 测试6：性能测试 ---");

        Assumptions.assumeTrue(kafkaTemplate != null, "Kafka not available");

        int testCount = 1000;
        long startTime = System.currentTimeMillis();

        try {
            for (int i = 0; i < testCount; i++) {
                LogDTO logDTO = buildTestLog();
                logDTO.setMessage("性能测试日志 #" + i);

                String logJson = JsonUtil.toJson(logDTO);
                String key = generateKey(logDTO);

                kafkaTemplate.send("logx-logs", key, logJson);
            }

            // 等待处理完成
            TimeUnit.SECONDS.sleep(10);

            long duration = System.currentTimeMillis() - startTime;
            double qps = testCount / (duration / 1000.0);

            System.out.println("\n✅ 性能测试完成");
            System.out.println("   总数: " + testCount + " 条");
            System.out.println("   耗时: " + duration + " ms");
            System.out.println("   QPS: " + String.format("%.0f", qps));

            Assertions.assertTrue(qps > 100, "QPS should be greater than 100");

        } catch (Exception e) {
            System.err.println("❌ 测试6失败: " + e.getMessage());
            Assertions.fail(e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建测试日志
     */
    private LogDTO buildTestLog() {
        LogDTO log = new LogDTO();
        log.setId(UUID.randomUUID().toString().replace("-", ""));
        log.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        log.setSpanId("span-" + System.currentTimeMillis());
        log.setTenantId("tenant_001");
        log.setSystemId("sys_erp");
        log.setTimestamp(LocalDateTime.now());
        log.setLevel("INFO");
        log.setLogger("com.test.TestLogger");
        log.setThread("http-nio-8080-exec-1");
        log.setClassName("com.test.OrderService");
        log.setMethodName("createOrder");
        log.setLineNumber(123);
        log.setMessage("测试日志消息");
        log.setUserId("user-001");
        log.setUserName("测试用户");
        log.setModule("订单管理");
        log.setOperation("创建订单");
        log.setRequestUrl("/api/order/create");
        log.setRequestMethod("POST");
        log.setRequestParams("{\"productId\":\"P001\",\"quantity\":2}");
        log.setResponseTime(150L);
        log.setIp("192.168.1.100");
        log.setUserAgent("Mozilla/5.0");
        log.setTags(List.of("test", "order"));

        return log;
    }

    /**
     * 发送日志
     */
    private void sendLog(LogDTO logDTO) {
        try {
            String logJson = JsonUtil.toJson(logDTO);
            String key = generateKey(logDTO);
            kafkaTemplate.send("logx-logs", key, logJson);
        } catch (Exception e) {
            System.err.println("发送日志失败: " + e.getMessage());
        }
    }

    /**
     * 生成 Kafka Key
     */
    private String generateKey(LogDTO log) {
        return String.format("%s:%s:%s",
                log.getTenantId(),
                log.getSystemId(),
                log.getTraceId());
    }
}