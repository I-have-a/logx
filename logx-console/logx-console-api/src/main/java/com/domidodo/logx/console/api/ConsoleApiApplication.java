package com.domidodo.logx.console.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 管理控制台 API 启动类
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.domidodo.logx.console.api",
        "com.domidodo.logx.infrastructure",
        "com.domidodo.logx.common"
})
public class ConsoleApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsoleApiApplication.class, args);
        log.info("========================================");
        log.info("LogX Console API Application Started");
        log.info("Swagger UI: http://localhost:8083/doc.html");
        log.info("========================================");
    }
}