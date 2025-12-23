package com.domidodo.logx.sdk.spring.aspect;

import com.domidodo.logx.sdk.spring.context.UserContextProvider;
import com.domidodo.logx.sdk.spring.properties.LogXProperties;
import com.domidodo.logx.sdk.core.LogXClient;
import com.domidodo.logx.sdk.core.model.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LogX AOP 日志切面
 * 支持自动获取用户上下文信息
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class LogAspect {

    private final LogXClient logXClient;
    private final LogXProperties properties;
    private final UserContextProvider userContextProvider;

    /**
     * Controller 切入点
     */
    @Pointcut("@within(org.springframework.web.bind.annotation.RestController) || " +
              "@within(org.springframework.stereotype.Controller)")
    public void controllerPointcut() {}

    /**
     * Service 切入点
     */
    @Pointcut("@within(org.springframework.stereotype.Service)")
    public void servicePointcut() {}

    /**
     * Controller 日志收集
     */
    @Around("controllerPointcut()")
    public Object aroundController(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.getAspect().isController()) {
            return joinPoint.proceed();
        }
        return logMethod(joinPoint, "Controller");
    }

    /**
     * Service 日志收集
     */
    @Around("servicePointcut()")
    public Object aroundService(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.getAspect().isService()) {
            return joinPoint.proceed();
        }
        return logMethod(joinPoint, "Service");
    }

    private Object logMethod(ProceedingJoinPoint joinPoint, String type) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = method.getName();

        // 获取 HTTP 请求
        HttpServletRequest request = getRequest();

        // 构建日志实体
        LogEntry.LogEntryBuilder entryBuilder = LogEntry.builder()
                .module(extractModuleName(className))
                .operation(type + "." + methodName)
                .className(className)
                .methodName(methodName);

        // 获取用户上下文信息
        if (request != null && properties.getUserContext().isEnabled() && userContextProvider != null) {
            try {
                String userId = userContextProvider.getUserId(request);
                String userName = userContextProvider.getUserName(request);
                String tenantId = userContextProvider.getTenantId(request);

                if (userId != null) {
                    entryBuilder.userId(userId);
                }
                if (userName != null) {
                    entryBuilder.userName(userName);
                }
                if (tenantId != null) {
                    entryBuilder.tenantId(tenantId);
                }
            } catch (Exception e) {
                log.debug("无法获取用户上下文", e);
            }
        }

        // 获取 HTTP 请求信息
        Map<String, Object> context = new HashMap<>();
        context.put("type", type);

        if (request != null) {
            String uri = request.getRequestURI();
            String requestMethod = request.getMethod();
            String ip = getClientIp(request);

            entryBuilder
                    .requestUrl(uri)
                    .requestMethod(requestMethod)
                    .ip(ip);

            context.put("uri", uri);
            context.put("method", requestMethod);
            context.put("ip", ip);
        }

        // 记录参数
        if (properties.getAspect().isLogArgs()) {
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                context.put("args", argsToString(args));
            }
        }

        long startTime = System.currentTimeMillis();
        Object result = null;
        boolean success = true;
        LogEntry logEntry;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            success = false;

            // 记录异常日志
            logEntry = entryBuilder
                    .level("ERROR")
                    .message(String.format("%s 执行异常: %s.%s", type, className, methodName))
                    .context(context)
                    .build();

            logEntry.setThrowable(e);
            logXClient.log(logEntry);

            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            context.put("duration", duration + "ms");

            // 设置响应时间
            entryBuilder.responseTime(duration);

            // 记录结果
            if (success) {
                if (properties.getAspect().isLogResult() && result != null) {
                    String resultStr = result.toString();
                    context.put("result", resultStr.length() > 200 ?
                            resultStr.substring(0, 200) + "..." : resultStr);
                }

                // 慢请求告警
                if (duration > properties.getAspect().getSlowThreshold()) {
                    logEntry = entryBuilder
                            .level("WARN")
                            .message(String.format("%s 慢请求: %s.%s 耗时 %dms",
                                    type, className, methodName, duration))
                            .context(context)
                            .build();

                    // 添加慢请求标签
                    logEntry.setTags(List.of("slow-request", type.toLowerCase()));
                } else {
                    logEntry = entryBuilder
                            .level("INFO")
                            .message(String.format("%s 执行: %s.%s", type, className, methodName))
                            .context(context)
                            .build();
                }

                logXClient.log(logEntry);
            }
        }
    }

    /**
     * 获取当前HTTP请求
     */
    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 处理多级代理的情况
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 参数转字符串
     */
    private String argsToString(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];

            // 跳过HttpServletRequest等不需要记录的参数
            if (arg instanceof HttpServletRequest ||
                arg instanceof jakarta.servlet.http.HttpServletResponse ||
                arg instanceof org.springframework.ui.Model) {
                sb.append(arg.getClass().getSimpleName());
            } else if (arg != null) {
                sb.append(arg.getClass().getSimpleName()).append(":");
                // 避免输出过长的参数
                String str = arg.toString();
                if (str.length() > 200) {
                    sb.append(str, 0, 200).append("...");
                } else {
                    sb.append(str);
                }
            } else {
                sb.append("null");
            }

            if (i < args.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 从类名提取模块名
     */
    private String extractModuleName(String className) {
        // 尝试从包名提取模块名
        // 例如: com.example.user.controller.UserController -> user
        String[] parts = className.split("\\.");
        if (parts.length >= 3) {
            // 倒数第二个部分通常是模块名或层级名
            String modulePart = parts[parts.length - 2];
            // 如果是 controller, service 等，再往前找一个
            if (modulePart.equalsIgnoreCase("controller") ||
                modulePart.equalsIgnoreCase("service") ||
                modulePart.equalsIgnoreCase("api")) {
                if (parts.length >= 4) {
                    return parts[parts.length - 3];
                }
            }
            return modulePart;
        }
        return "unknown";
    }
}