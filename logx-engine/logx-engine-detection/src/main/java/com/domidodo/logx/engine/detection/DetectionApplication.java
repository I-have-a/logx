package com.domidodo.logx.engine.detection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 异常检测引擎启动类
 */
@Slf4j
@EnableAsync
@EnableScheduling
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.domidodo.logx.engine.detection",
        "com.domidodo.logx.infrastructure",
        "com.domidodo.logx.common"
})
public class DetectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DetectionApplication.class, args);
        log.info("========================================");
        log.info("LogX Detection Engine Started");
        log.info("异常检测引擎已启动");
        log.info("========================================");
    }
}