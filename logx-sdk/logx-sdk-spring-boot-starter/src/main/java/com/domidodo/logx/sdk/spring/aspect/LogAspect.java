package com.domidodo.logx.sdk.spring.aspect;


import com.domidodo.logx.sdk.spring.properties.LogXProperties;
import com.domidodo.logx.sdk.core.LogXClient;
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
import java.util.Map;

@Slf4j
@Aspect
@RequiredArgsConstructor
public class LogAspect {

    private final LogXClient logXClient;
    private final LogXProperties properties;

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

        // 构建上下文
        Map<String, Object> context = new HashMap<>();
        context.put("type", type);
        context.put("className", className);
        context.put("methodName", methodName);

        // 获取 HTTP 请求信息
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            context.put("uri", request.getRequestURI());
            context.put("method", request.getMethod());
            context.put("ip", getClientIp(request));
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

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            success = false;

            // 记录异常
            logXClient.error(
                    String.format("%s 执行异常: %s.%s", type, className, methodName),
                    e,
                    context
            );

            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            context.put("duration", duration + "ms");

            // 记录结果
            if (success) {
                if (properties.getAspect().isLogResult() && result != null) {
                    context.put("result", result.toString());
                }

                // 慢请求告警
                if (duration > properties.getAspect().getSlowThreshold()) {
                    logXClient.warn(
                            String.format("%s 慢请求: %s.%s 耗时 %dms", type, className, methodName, duration),
                            context
                    );
                } else {
                    logXClient.info(
                            String.format("%s 执行: %s.%s", type, className, methodName),
                            context
                    );
                }
            }
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
            ip = request.getRemoteAddr();
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
            if (arg != null) {
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
}
