package com.domidodo.logx.sdk.spring.autoconfigure;

import com.domidodo.logx.sdk.spring.aspect.LogAspect;
import com.domidodo.logx.sdk.spring.properties.LogXProperties;
import com.domidodo.logx.sdk.core.LogXClient;
import com.domidodo.logx.sdk.core.LogXLogger;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(LogXProperties.class)
@ConditionalOnProperty(prefix = "logx", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogXAutoConfiguration {

    private LogXClient logXClient;

    @Bean
    @ConditionalOnMissingBean
    public LogXClient logXClient(LogXProperties properties) {
        log.info("初始化 LogX SDK...");

        // 验证配置
        validateConfig(properties);

        // 创建客户端 Builder
        LogXClient.Builder builder = LogXClient.builder()
                .tenantId(properties.getTenantId())
                .systemId(properties.getSystemId())
                .systemName(properties.getSystemName())
                .apiKey(properties.getApiKey())
                .mode(properties.getMode())
                .bufferEnabled(properties.getBuffer().isEnabled())
                .bufferSize(properties.getBuffer().getSize());

        // 根据模式设置网关配置
        if ("grpc".equalsIgnoreCase(properties.getMode())) {
            builder.grpcEndpoint(
                    properties.getGateway().getHost(),
                    properties.getGateway().getPort()
            );
            log.info("LogX SDK 使用 gRPC 模式 [{}:{}]",
                    properties.getGateway().getHost(),
                    properties.getGateway().getPort());
        } else {
            builder.gatewayUrl(properties.getGateway().getUrl());
            log.info("LogX SDK 使用 HTTP 模式 [{}]",
                    properties.getGateway().getUrl());
        }

        // 构建客户端
        logXClient = builder.build();

        // 初始化静态日志记录器
        LogXLogger.initClient(logXClient);

        log.info("LogX SDK 初始化完成 [租户:{}, 系统:{}]",
                properties.getTenantId(), properties.getSystemName());

        return logXClient;
    }

    @Bean
    @ConditionalOnProperty(prefix = "logx.aspect", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LogAspect logAspect(LogXClient logXClient, LogXProperties properties) {
        log.info("启用 LogX AOP 自动日志收集");
        return new LogAspect(logXClient, properties);
    }

    @PreDestroy
    public void destroy() {
        if (logXClient != null) {
            log.info("关闭 LogX SDK...");
            logXClient.shutdown();
        }
    }

    private void validateConfig(LogXProperties properties) {
        if (properties.getTenantId() == null) {
            throw new IllegalArgumentException("logx.tenant-id 不能为空");
        }
        if (properties.getSystemId() == null) {
            throw new IllegalArgumentException("logx.system-id 不能为空");
        }
        if (properties.getSystemName() == null || properties.getSystemName().isEmpty()) {
            throw new IllegalArgumentException("logx.system-name 不能为空");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isEmpty()) {
            throw new IllegalArgumentException("logx.api-key 不能为空");
        }

        // 验证网关配置
        if ("grpc".equalsIgnoreCase(properties.getMode())) {
            if (properties.getGateway().getHost() == null || properties.getGateway().getHost().isEmpty()) {
                throw new IllegalArgumentException("gRPC 模式下 logx.gateway.host 不能为空");
            }
            if (properties.getGateway().getPort() <= 0) {
                throw new IllegalArgumentException("gRPC 模式下 logx.gateway.port 必须大于 0");
            }
        } else {
            if (properties.getGateway().getUrl() == null || properties.getGateway().getUrl().isEmpty()) {
                throw new IllegalArgumentException("HTTP 模式下 logx.gateway.url 不能为空");
            }
        }
    }
}