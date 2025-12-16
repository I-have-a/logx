package com.domidodo.gateway.http;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.domidodo.logx",
        "com.domidodo.logx.infrastructure",
        "com.domidodo.gateway.http"
})
public class HttpGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(HttpGatewayApplication.class, args);
    }
}
