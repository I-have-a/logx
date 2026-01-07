package com.domidodo.logx.sdk.spring.filter;

import com.domidodo.logx.sdk.core.context.TraceContext;
import com.domidodo.logx.sdk.core.context.TraceContext.TraceInfo;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;

import java.io.IOException;

/**
 * TraceId 过滤器（Servlet 环境）
 *
 * 核心功能：
 * 1. 从请求头接收网关传递的 TraceId
 * 2. 如果没有 TraceId 则自动生成（直接访问服务的场景）
 * 3. 存入 ThreadLocal 供 LogAspect 等组件使用
 * 4. 请求结束时清理 ThreadLocal
 */
@Slf4j
public class TraceIdFilter implements Filter, Ordered {

    private final String traceIdHeader;
    private final String spanIdHeader;
    private final String userIdHeader;
    private final String userNameHeader;
    private final String tenantIdHeader;

    /**
     * 使用默认 Header 名称
     */
    public TraceIdFilter() {
        this(
                TraceContext.TRACE_ID_HEADER,
                TraceContext.SPAN_ID_HEADER,
                TraceContext.USER_ID_HEADER,
                TraceContext.USER_NAME_HEADER,
                TraceContext.TENANT_ID_HEADER
        );
    }

    /**
     * 自定义 Header 名称
     */
    public TraceIdFilter(String traceIdHeader, String spanIdHeader,
                         String userIdHeader, String userNameHeader, String tenantIdHeader) {
        this.traceIdHeader = traceIdHeader;
        this.spanIdHeader = spanIdHeader;
        this.userIdHeader = userIdHeader;
        this.userNameHeader = userNameHeader;
        this.tenantIdHeader = tenantIdHeader;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        try {
            // 1. 从请求头获取追踪信息（网关传递的）
            String traceId = httpRequest.getHeader(traceIdHeader);
            String parentSpanId = httpRequest.getHeader(spanIdHeader);

            // 2. 创建追踪信息
            // 如果没有 TraceId（直接访问服务），则生成新的
            TraceInfo traceInfo = TraceContext.createTrace(traceId, parentSpanId);

            // 3. 获取用户信息（网关传递的）
            traceInfo.setUserId(httpRequest.getHeader(userIdHeader));
            traceInfo.setUserName(httpRequest.getHeader(userNameHeader));
            traceInfo.setTenantId(httpRequest.getHeader(tenantIdHeader));

            // 4. 存入 ThreadLocal
            TraceContext.setTrace(traceInfo);

            if (log.isDebugEnabled()) {
                log.debug("设置追踪上下文 [traceId={}, spanId={}, userId={}]",
                        traceInfo.getTraceId().substring(0, 8),
                        traceInfo.getSpanId().substring(0, 8),
                        traceInfo.getUserId());
            }

            // 5. 继续执行请求
            chain.doFilter(request, response);

        } finally {
            // 6. 清理 ThreadLocal（非常重要，防止内存泄漏）
            TraceContext.clear();
        }
    }

    @Override
    public int getOrder() {
        // 最高优先级，确保在其他 Filter 之前执行
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
