package com.domidodo.logx.gateway.grpc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * gRPC 网关启动类
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.domidodo.logx.gateway.grpc",
        "com.domidodo.logx.infrastructure",
        "com.domidodo.logx.common"
})
public class GrpcGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrpcGatewayApplication.class, args);
        log.info("========================================");
        log.info("LogX gRPC Gateway Application Started");
        log.info("gRPC Server listening on port: 9090");
        log.info("========================================");
    }
}