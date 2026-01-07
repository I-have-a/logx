package com.domidodo.logx.sdk.gateway.config;

import com.domidodo.logx.sdk.core.LogXClient;
import com.domidodo.logx.sdk.gateway.filter.LogXGatewayFilter;
import com.domidodo.logx.sdk.gateway.properties.LogXGatewayProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;

/**
 * LogX 网关自动配置
 * 
 * 仅在以下条件满足时生效：
 * 1. WebFlux 响应式环境
 * 2. 存在 Spring Cloud Gateway
 * 3. 配置 logx.gateway.enabled=true（默认）
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LogXGatewayProperties.class)
@ConditionalOnProperty(prefix = "logx.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({GlobalFilter.class, DispatcherHandler.class})
public class LogXGatewayAutoConfiguration {

    private LogXClient logXClient;

    /**
     * 创建 LogX 客户端
     */
    @Bean
    @ConditionalOnMissingBean
    public LogXClient logXClient(LogXGatewayProperties properties) {
        log.info("初始化 LogX Gateway SDK...");

        // 验证配置
        validateConfig(properties);

        // 构建客户端
        LogXClient.Builder builder = LogXClient.builder()
                .tenantId(properties.getTenantId())
                .systemId(properties.getSystemId())
                .systemName(properties.getSystemName())
                .apiKey(properties.getApiKey())
                .mode(properties.getMode())
                .bufferEnabled(properties.getBuffer().isEnabled())
                .bufferSize(properties.getBuffer().getSize())
                .flushInterval(properties.getBuffer().getFlushInterval());

        // 设置服务端配置
        if ("grpc".equalsIgnoreCase(properties.getMode())) {
            builder.grpcEndpoint(
                            properties.getServer().getHost(),
                            properties.getServer().getPort()
                    )
                    .batchMode(properties.getServer().getBatchMode());

            log.info("LogX Gateway SDK 使用 gRPC 模式 [{}:{}]",
                    properties.getServer().getHost(),
                    properties.getServer().getPort());
        } else {
            builder.gatewayUrl(properties.getServer().getUrl());
            log.info("LogX Gateway SDK 使用 HTTP 模式 [{}]",
                    properties.getServer().getUrl());
        }

        builder.connectTimeout(properties.getServer().getConnectTimeout())
                .readTimeout(properties.getServer().getReadTimeout());

        logXClient = builder.build();

        log.info("LogX Gateway SDK 初始化完成 [租户:{}, 系统:{}, 追踪:{}]",
                properties.getTenantId(),
                properties.getSystemName(),
                properties.getTrace().isEnabled());

        return logXClient;
    }

    /**
     * 创建网关全局过滤器
     */
    @Bean
    @ConditionalOnProperty(prefix = "logx.gateway.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LogXGatewayFilter logXGatewayFilter(LogXClient logXClient, LogXGatewayProperties properties) {
        log.info("启用 LogX 网关过滤器 [排除前缀:{}, 慢请求阈值:{}ms]",
                properties.getLog().getExcludePathPrefixes(),
                properties.getLog().getSlowThreshold());
        return new LogXGatewayFilter(logXClient, properties);
    }

    @PreDestroy
    public void destroy() {
        if (logXClient != null) {
            log.info("关闭 LogX Gateway SDK...");
            logXClient.shutdown();
        }
    }

    /**
     * 验证必填配置
     */
    private void validateConfig(LogXGatewayProperties properties) {
        if (properties.getTenantId() == null || properties.getTenantId().isEmpty()) {
            throw new IllegalArgumentException("logx.gateway.tenant-id 不能为空");
        }
        if (properties.getSystemId() == null || properties.getSystemId().isEmpty()) {
            throw new IllegalArgumentException("logx.gateway.system-id 不能为空");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isEmpty()) {
            throw new IllegalArgumentException("logx.gateway.api-key 不能为空");
        }

        // 验证服务端配置
        if ("grpc".equalsIgnoreCase(properties.getMode())) {
            if (properties.getServer().getHost() == null || properties.getServer().getHost().isEmpty()) {
                throw new IllegalArgumentException("gRPC 模式下 logx.gateway.server.host 不能为空");
            }
            if (properties.getServer().getPort() <= 0) {
                throw new IllegalArgumentException("gRPC 模式下 logx.gateway.server.port 必须大于 0");
            }
        } else {
            if (properties.getServer().getUrl() == null || properties.getServer().getUrl().isEmpty()) {
                throw new IllegalArgumentException("HTTP 模式下 logx.gateway.server.url 不能为空");
            }
        }
    }
}
