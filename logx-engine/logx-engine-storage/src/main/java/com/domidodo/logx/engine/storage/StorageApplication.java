package com.domidodo.logx.engine.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 存储模块启动类
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "com.domidodo.logx.engine.storage",
        "com.domidodo.logx.infrastructure"
})
public class StorageApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorageApplication.class, args);
    }
}