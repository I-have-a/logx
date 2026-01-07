package com.domidodo.logx.sdk.gateway.filter;

import com.domidodo.logx.sdk.core.LogXClient;
import com.domidodo.logx.sdk.core.context.TraceContext;
import com.domidodo.logx.sdk.core.context.TraceContext.TraceInfo;
import com.domidodo.logx.sdk.core.model.LogEntry;
import com.domidodo.logx.sdk.gateway.properties.LogXGatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LogX 网关全局过滤器
 * 
 * 核心功能：
 * 1. 生成/接收 TraceId（入口请求生成，已有则复用）
 * 2. 向下游服务传递追踪信息（通过 HTTP Header）
 * 3. 记录网关层访问日志
 * 4. 标记慢请求、错误请求
 */
@Slf4j
public class LogXGatewayFilter implements GlobalFilter, Ordered {

    private final LogXClient logXClient;
    private final LogXGatewayProperties properties;

    private static final String START_TIME_ATTR = "logx-start-time";
    private static final String TRACE_INFO_ATTR = "logx-trace-info";

    public LogXGatewayFilter(LogXClient logXClient, LogXGatewayProperties properties) {
        this.logXClient = logXClient;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 1. 检查是否需要排除
        if (shouldExclude(path)) {
            return chain.filter(exchange);
        }

        // 2. 创建或获取追踪信息
        TraceInfo traceInfo = createTraceInfo(request);

        // 3. 记录开始时间和追踪信息
        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());
        exchange.getAttributes().put(TRACE_INFO_ATTR, traceInfo);

        // 4. 构建新请求，注入追踪头到下游
        ServerHttpRequest mutatedRequest = injectTraceHeaders(request, traceInfo);

        // 5. 执行过滤链
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doFinally(signal -> {
                    try {
                        recordAccessLog(exchange, traceInfo);
                    } catch (Exception e) {
                        log.error("记录网关日志失败", e);
                    }
                });
    }

    /**
     * 创建追踪信息
     */
    private TraceInfo createTraceInfo(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        LogXGatewayProperties.Trace traceConfig = properties.getTrace();

        // 尝试从请求头获取已有的 TraceId（可能来自外部系统）
        String traceId = headers.getFirst(traceConfig.getTraceIdHeader());
        String parentSpanId = headers.getFirst(traceConfig.getSpanIdHeader());

        // 创建追踪信息
        TraceInfo traceInfo = TraceContext.createTrace(traceId, parentSpanId);

        // 获取用户信息
        traceInfo.setUserId(headers.getFirst(traceConfig.getUserIdHeader()));
        traceInfo.setUserName(headers.getFirst(traceConfig.getUserNameHeader()));
        traceInfo.setTenantId(headers.getFirst(traceConfig.getTenantIdHeader()));

        return traceInfo;
    }

    /**
     * 注入追踪头到下游请求
     */
    private ServerHttpRequest injectTraceHeaders(ServerHttpRequest request, TraceInfo traceInfo) {
        LogXGatewayProperties.Trace traceConfig = properties.getTrace();
        ServerHttpRequest.Builder builder = request.mutate();

        // 注入 TraceId（核心：确保下游服务能接收到）
        builder.header(traceConfig.getTraceIdHeader(), traceInfo.getTraceId());
        
        // 注入当前 SpanId（作为下游的 Parent SpanId）
        builder.header(traceConfig.getSpanIdHeader(), traceInfo.getSpanId());

        // 注入父 SpanId
        if (traceInfo.getParentSpanId() != null) {
            builder.header(TraceContext.PARENT_SPAN_ID_HEADER, traceInfo.getParentSpanId());
        }

        // 传递用户信息
        if (traceConfig.isPropagateUser()) {
            if (traceInfo.getUserId() != null) {
                builder.header(traceConfig.getUserIdHeader(), traceInfo.getUserId());
            }
            if (traceInfo.getUserName() != null) {
                builder.header(traceConfig.getUserNameHeader(), traceInfo.getUserName());
            }
            if (traceInfo.getTenantId() != null) {
                builder.header(traceConfig.getTenantIdHeader(), traceInfo.getTenantId());
            }
        }

        return builder.build();
    }

    /**
     * 记录访问日志
     */
    private void recordAccessLog(ServerWebExchange exchange, TraceInfo traceInfo) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // 计算响应时间
        Long startTime = exchange.getAttribute(START_TIME_ATTR);
        long responseTime = startTime != null ? System.currentTimeMillis() - startTime : 0;

        // 获取状态码
        HttpStatusCode statusCode = response.getStatusCode();
        int status = statusCode != null ? statusCode.value() : 0;

        // 构建日志条目
        LogEntry entry = LogEntry.builder()
                .tenantId(properties.getTenantId())
                .systemId(properties.getSystemId())
                .systemName(properties.getSystemName())
                .traceId(traceInfo.getTraceId())
                .spanId(traceInfo.getSpanId())
                .timestamp(LocalDateTime.now())
                .level(determineLogLevel(status, responseTime))
                .logger("GatewayAccessLog")
                .thread(Thread.currentThread().getName())
                .className("LogXGatewayFilter")
                .methodName("filter")
                .message(buildLogMessage(request, status, responseTime))
                .requestUrl(request.getPath().value())
                .requestMethod(request.getMethod().name())
                .responseTime(responseTime)
                .ip(getClientIp(request))
                .userAgent(request.getHeaders().getFirst(HttpHeaders.USER_AGENT))
                .userId(traceInfo.getUserId())
                .userName(traceInfo.getUserName())
                .module("Gateway")
                .operation("HTTP_REQUEST")
                .build();

        // 记录请求参数
        String queryString = request.getURI().getQuery();
        if (queryString != null && !queryString.isEmpty()) {
            entry.setRequestParams(queryString);
        }

        // 添加扩展信息
        Map<String, Object> context = new HashMap<>();
        context.put("statusCode", status);
        context.put("host", request.getHeaders().getFirst(HttpHeaders.HOST));
        
        // 记录请求头
        if (properties.getLog().isLogHeaders()) {
            context.put("requestHeaders", extractHeaders(request.getHeaders()));
        }
        entry.setContext(context);

        // 添加标签
        entry.addTag("gateway");
        if (responseTime > properties.getLog().getSlowThreshold()) {
            entry.addTag("slow-request");
        }
        if (status >= 400 && status < 500) {
            entry.addTag("client-error");
        }
        if (status >= 500) {
            entry.addTag("server-error");
        }

        // 发送日志
        logXClient.send(entry);

        // 本地调试日志
        if (log.isDebugEnabled()) {
            log.debug("[{}] {} {} -> {} ({}ms)",
                    traceInfo.getTraceId().substring(0, 8),
                    request.getMethod(),
                    request.getPath().value(),
                    status,
                    responseTime);
        }
    }

    /**
     * 构建日志消息
     */
    private String buildLogMessage(ServerHttpRequest request, int status, long responseTime) {
        return String.format("%s %s -> %d (%dms)",
                request.getMethod(),
                request.getPath().value(),
                status,
                responseTime);
    }

    /**
     * 确定日志级别
     */
    private String determineLogLevel(int statusCode, long responseTime) {
        if (statusCode >= 500) {
            return "ERROR";
        } else if (statusCode >= 400) {
            return "WARN";
        } else if (responseTime > properties.getLog().getSlowThreshold()) {
            return "WARN";
        }
        return "INFO";
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();

        // 依次尝试从代理头获取
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };

        for (String headerName : headerNames) {
            String ip = headers.getFirst(headerName);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能包含多个IP，取第一个
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        // 获取远程地址
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * 提取需要记录的请求头
     */
    private Map<String, String> extractHeaders(HttpHeaders headers) {
        List<String> includeHeaders = properties.getLog().getIncludeHeaders();
        return includeHeaders.stream()
                .filter(headers::containsKey)
                .collect(Collectors.toMap(
                        name -> name,
                        name -> {
                            List<String> values = headers.get(name);
                            return values != null && !values.isEmpty() ? values.get(0) : "";
                        }
                ));
    }

    /**
     * 检查路径是否需要排除
     */
    private boolean shouldExclude(String path) {
        // 检查完整路径
        if (properties.getLog().getExcludePaths().contains(path)) {
            return true;
        }

        // 检查路径前缀
        for (String prefix : properties.getLog().getExcludePathPrefixes()) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getOrder() {
        // 最高优先级，确保最早执行（在其他过滤器之前）
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
