package com.domidodo.logx.sdk.spring.aspect;

import com.domidodo.logx.sdk.core.LogXClient;
import com.domidodo.logx.sdk.core.context.TraceContext;
import com.domidodo.logx.sdk.core.context.TraceContext.TraceInfo;
import com.domidodo.logx.sdk.core.model.LogEntry;
import com.domidodo.logx.sdk.spring.annotation.LogIgnore;
import com.domidodo.logx.sdk.spring.annotation.LogModule;
import com.domidodo.logx.sdk.spring.annotation.LogOperation;
import com.domidodo.logx.sdk.spring.context.UserContextProvider;
import com.domidodo.logx.sdk.spring.properties.LogXProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 日志切面
 * <p>
 * 核心改进：
 * 1. 自动从 TraceContext 获取 TraceId（网关传递的）
 * 2. 支持 @LogModule、@LogOperation、@LogIgnore 注解
 * 3. 自动记录请求参数、响应结果、异常信息
 */
@Slf4j
@Aspect
public class LogAspect {

    private final LogXClient logXClient;
    private final LogXProperties properties;
    private final UserContextProvider userContextProvider;

    public LogAspect(LogXClient logXClient, LogXProperties properties, UserContextProvider userContextProvider) {
        this.logXClient = logXClient;
        this.properties = properties;
        this.userContextProvider = userContextProvider;
    }

    /**
     * Controller 切点
     */
    @Pointcut("@within(org.springframework.web.bind.annotation.RestController) || " +
              "@within(org.springframework.stereotype.Controller)")
    public void controllerPointcut() {
    }

    /**
     * Service 切点
     */
    @Pointcut("@within(org.springframework.stereotype.Service)")
    public void servicePointcut() {
    }

    /**
     * Controller 日志收集
     */
    @Around("controllerPointcut()")
    public Object aroundController(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.getAspect().isController()) {
            return joinPoint.proceed();
        }
        return doAround(joinPoint, "Controller");
    }

    /**
     * Service 日志收集
     */
    @Around("servicePointcut()")
    public Object aroundService(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.getAspect().isService()) {
            return joinPoint.proceed();
        }
        return doAround(joinPoint, "Service");
    }

    /**
     * 核心日志处理逻辑
     */
    private Object doAround(ProceedingJoinPoint joinPoint, String layer) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 检查是否忽略
        if (method.isAnnotationPresent(LogIgnore.class) ||
            joinPoint.getTarget().getClass().isAnnotationPresent(LogIgnore.class)) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            exception = e;
            throw e;
        } finally {
            try {
                long responseTime = System.currentTimeMillis() - startTime;
                recordLog(joinPoint, method, layer, result, exception, responseTime);
            } catch (Exception e) {
                log.error("记录日志失败", e);
            }
        }
    }

    /**
     * 记录日志
     */
    private void recordLog(ProceedingJoinPoint joinPoint, Method method, String layer,
                           Object result, Throwable exception, long responseTime) {

        // 获取请求上下文
        HttpServletRequest request = getRequest();

        // ★ 核心改进：从 TraceContext 获取追踪信息
        TraceInfo traceInfo = TraceContext.getTrace();

        // 构建日志条目
        LogEntry.LogEntryBuilder builder = LogEntry.builder()
                .tenantId(properties.getTenantId())
                .systemId(properties.getSystemId())
                .systemName(properties.getSystemName())
                .timestamp(LocalDateTime.now())
                .logger(joinPoint.getTarget().getClass().getName())
                .thread(Thread.currentThread().getName())
                .className(joinPoint.getTarget().getClass().getSimpleName())
                .methodName(method.getName())
                .responseTime(responseTime);

        // ★ 设置追踪信息（从 TraceContext 获取）
        if (traceInfo != null) {
            builder.traceId(traceInfo.getTraceId());
            builder.spanId(traceInfo.getSpanId());

            // 优先使用 TraceContext 中的用户信息（网关传递的）
            if (traceInfo.getUserId() != null) {
                builder.userId(traceInfo.getUserId());
            }
            if (traceInfo.getUserName() != null) {
                builder.userName(traceInfo.getUserName());
            }
        }

        // 从 UserContextProvider 补充用户信息（如果 TraceContext 中没有）
        if (request != null && userContextProvider != null) {
            if (traceInfo == null || traceInfo.getUserId() == null) {
                builder.userId(userContextProvider.getUserId(request));
            }
            if (traceInfo == null || traceInfo.getUserName() == null) {
                builder.userName(userContextProvider.getUserName(request));
            }
        }

        // 设置请求信息
        if (request != null) {
            builder.requestUrl(request.getRequestURI());
            builder.requestMethod(request.getMethod());
            builder.ip(getClientIp(request));
            builder.userAgent(request.getHeader("User-Agent"));
        }

        // 设置模块和操作
        String module = getModule(joinPoint, method);
        String operation = getOperation(method);
        builder.module(module);
        builder.operation(operation);

        // 设置日志级别和消息
        String level;
        String message;
        if (exception != null) {
            level = "ERROR";
            message = String.format("%s.%s() 执行异常: %s",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    method.getName(),
                    exception.getMessage());
            builder.exception(getStackTrace(exception));
        } else if (responseTime > properties.getAspect().getSlowThreshold()) {
            level = "WARN";
            message = String.format("%s.%s() 执行缓慢 (%dms)",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    method.getName(),
                    responseTime);
        } else {
            level = "INFO";
            message = String.format("%s.%s() 执行完成 (%dms)",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    method.getName(),
                    responseTime);
        }
        builder.level(level);
        builder.message(message);

        // 构建扩展信息
        Map<String, Object> context = new HashMap<>();
        context.put("layer", layer);

        // 记录请求参数
        if (properties.getAspect().isLogArgs()) {
            try {
                Object[] args = joinPoint.getArgs();
                if (args != null && args.length > 0) {
                    context.put("args", serializeArgs(args));
                }
            } catch (Exception e) {
                log.debug("序列化参数失败", e);
            }
        }

        // 记录响应结果
        if (properties.getAspect().isLogResult() && result != null && exception == null) {
            try {
                context.put("result", result.toString());
            } catch (Exception e) {
                log.debug("序列化结果失败", e);
            }
        }

        builder.context(context);

        // 添加标签
        LogEntry entry = builder.build();
        entry.addTag(layer.toLowerCase());
        if (responseTime > properties.getAspect().getSlowThreshold()) {
            entry.addTag("slow-request");
        }
        if (exception != null) {
            entry.addTag("exception");
        }

        // 发送日志
        logXClient.log(entry);
    }

    /**
     * 获取模块名
     */
    private String getModule(ProceedingJoinPoint joinPoint, Method method) {
        // 1. 检查方法注解
        LogModule methodModule = method.getAnnotation(LogModule.class);
        if (methodModule != null) {
            return methodModule.value();
        }

        // 2. 检查类注解
        LogModule classModule = joinPoint.getTarget().getClass().getAnnotation(LogModule.class);
        if (classModule != null) {
            return classModule.value();
        }

        // 3. 使用配置的映射
        if (properties.getModule().isEnabled()) {
            String className = joinPoint.getTarget().getClass().getName();

            // 检查类映射
            String module = properties.getModule().getClassMapping().get(className);
            if (module != null) {
                return module;
            }

            // 检查包映射
            for (Map.Entry<String, String> entry : properties.getModule().getPackageMapping().entrySet()) {
                if (className.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        // 4. 返回默认值
        return properties.getModule().getDefaultModule();
    }

    /**
     * 获取操作名
     */
    private String getOperation(Method method) {
        LogOperation operation = method.getAnnotation(LogOperation.class);
        if (operation != null) {
            return operation.value();
        }
        return method.getName();
    }

    /**
     * 获取请求对象
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
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * 序列化参数
     */
    private String serializeArgs(Object[] args) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            Object arg = args[i];
            if (arg == null) {
                sb.append("null");
            } else if (arg instanceof HttpServletRequest) {
                sb.append("HttpServletRequest");
            } else {
                try {
                    String str = arg.toString();
                    if (str.length() > 200) {
                        str = str.substring(0, 200) + "...";
                    }
                    sb.append(str);
                } catch (Exception e) {
                    sb.append(arg.getClass().getSimpleName());
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 获取异常堆栈
     */
    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
