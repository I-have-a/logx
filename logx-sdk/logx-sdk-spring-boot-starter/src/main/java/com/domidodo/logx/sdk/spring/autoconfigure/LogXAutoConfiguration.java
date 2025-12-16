package com.domidodo.logx.sdk.spring.autoconfigure;


import com.domidodo.logx.sdk.spring.aspect.LogAspect;
import com.domidodo.logx.sdk.spring.properties.LogXProperties;
import com.domidodo.logx.sdk.core.LogXClient;
import com.domidodo.logx.sdk.core.LogXLogger;
import com.domidodo.logx.sdk.core.config.LogXConfig;
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

        LogXConfig config = new LogXConfig();
        config.setTenantId(properties.getTenantId());
        config.setSystemId(properties.getSystemId());
        config.setSystemName(properties.getSystemName());
        config.setGatewayUrl(properties.getGateway().getUrl());
        config.setConnectTimeout(properties.getGateway().getConnectTimeout());
        config.setReadTimeout(properties.getGateway().getReadTimeout());
        config.setBufferEnabled(properties.getBuffer().isEnabled());
        config.setBufferSize(properties.getBuffer().getSize());
        config.setFlushInterval(properties.getBuffer().getFlushInterval());

        // 创建客户端
        logXClient = LogXClient.builder()
                .tenantId(config.getTenantId())
                .systemId(config.getSystemId())
                .systemName(config.getSystemName())
                .gatewayUrl(config.getGatewayUrl())
                .bufferEnabled(config.isBufferEnabled())
                .bufferSize(config.getBufferSize())
                .build();

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
    }
}
