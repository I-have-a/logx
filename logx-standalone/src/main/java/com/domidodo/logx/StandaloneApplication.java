package com.domidodo.logx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * LogX 单体应用启动类
 * 集成所有功能模块：
 * - HTTP Gateway: 日志接收网关
 * - Processor: 日志处理引擎
 * - Storage: 存储管理
 * - Detection: 检测告警
 * - Console API: 管理控制台API
 */
@Slf4j
@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "com.domidodo.logx",
        "com.domidodo.gateway"  // 扫描 HTTP Gateway
})
public class StandaloneApplication {

    public static void main(String[] args) {
        try {
            ConfigurableApplicationContext context = SpringApplication.run(StandaloneApplication.class, args);
            Environment env = context.getEnvironment();
            logApplicationStartup(env);
        } catch (Exception e) {
            log.error("应用启动失败", e);
            System.exit(1);
        }
    }

    private static void logApplicationStartup(Environment env) {
        String protocol = "http";
        if (env.getProperty("server.ssl.key-store") != null) {
            protocol = "https";
        }

        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String hostAddress = "localhost";

        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("无法获取主机地址，使用 localhost");
        }

        String banner = """
                
                ----------------------------------------------------------
                    LogX 单体应用启动成功！
                ----------------------------------------------------------
                    应用名称:    %s
                    运行环境:    %s
                    访问地址:
                        本地:    %s://localhost:%s%s
                        外部:    %s://%s:%s%s
                
                    集成模块:
                        ✓ HTTP Gateway    - 日志接收网关
                        ✓ Processor       - 日志处理引擎
                        ✓ Storage         - 存储管理
                        ✓ Detection       - 检测告警
                        ✓ Console API     - 管理控制台
                
                    API文档:     %s://localhost:%s%s/doc.html
                    健康检查:    %s://localhost:%s%s/actuator/health
                ----------------------------------------------------------
                """;

        log.info(String.format(
                banner,
                env.getProperty("spring.application.name", "LogX-Standalone"),
                env.getActiveProfiles().length > 0 ?
                        String.join(", ", env.getActiveProfiles()) : "default",
                protocol, serverPort, contextPath,
                protocol, hostAddress, serverPort, contextPath,
                protocol, serverPort, contextPath,
                protocol, serverPort, contextPath
        ));

        // 打印配置信息
        log.info("数据库: {}", env.getProperty("spring.datasource.url"));
        log.info("Redis: {}:{}",
                env.getProperty("spring.data.redis.host", "localhost"),
                env.getProperty("spring.data.redis.port", "6379"));
        log.info("Elasticsearch: {}",
                env.getProperty("spring.elasticsearch.uris", "http://localhost:9200"));
        log.info("Kafka: {}",
                env.getProperty("spring.kafka.bootstrap-servers", "localhost:9092"));
    }
}