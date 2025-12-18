package com.domidodo.logx.sdk.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication()
public class SDKApplication {
    public static void main(String[] args) {
        SpringApplication.run(SDKApplication.class, args);
    }
}
