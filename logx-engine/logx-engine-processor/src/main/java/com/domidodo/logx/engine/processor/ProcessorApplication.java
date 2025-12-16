package com.domidodo.logx.engine.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 日志处理器启动类
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.domidodo.logx.engine.processor",
        "com.domidodo.logx.infrastructure",
        "com.domidodo.logx.common"
})
public class ProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessorApplication.class, args);
        log.info("========================================");
        log.info("LogX Processor Application Started");
        log.info("========================================");
    }
}