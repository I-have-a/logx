package com.domidodo.logx.sdk.spring.config;

import com.domidodo.logx.sdk.spring.filter.TraceIdFilter;
import com.domidodo.logx.sdk.spring.properties.LogXProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * TraceId 过滤器自动配置
 * <p>
 * 仅在 Servlet 环境下生效（排除 WebFlux 网关）
 */
@Slf4j
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "logx.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LogXProperties.class)
public class TraceIdFilterConfiguration {

    /**
     * 注册 TraceId 过滤器
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(LogXProperties properties) {
        log.info("注册 LogX TraceId 过滤器");

        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();

        // 使用配置的 Header 名称
        LogXProperties.UserContext userContext = properties.getUserContext();
        TraceIdFilter filter = new TraceIdFilter(
                userContext.getTraceIdHeader(),
                userContext.getSpanIdHeader(),
                userContext.getUserIdHeader(),
                userContext.getUserNameHeader(),
                userContext.getTenantIdHeader()
        );

        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setName("logxTraceIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);

        return registration;
    }
}
