package com.domidodo.logx.sdk.spring.aspect;

import com.domidodo.logx.sdk.spring.annotation.LogIgnore;
import com.domidodo.logx.sdk.spring.annotation.LogModule;
import com.domidodo.logx.sdk.spring.annotation.LogOperation;
import com.domidodo.logx.sdk.spring.context.UserContextProvider;
import com.domidodo.logx.sdk.spring.properties.LogXProperties;
import com.domidodo.logx.sdk.core.LogXClient;
import com.domidodo.logx.sdk.core.model.LogEntry;
import lombok.RequiredArgsConstructor;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.*;

/**
 * LogX AOP 日志切面
 * 支持注解和配置文件自定义模块名称
 * <p>
 * 模块名优先级：
 * 1. @LogModule 注解（方法级）
 * 2. @LogModule 注解（类级）
 * 3. 配置文件 class-mapping
 * 4. 配置文件 package-mapping
 * 5. 默认提取（从包名）
 * <p>
 * 操作名优先级：
 * 1. @LogOperation 注解
 * 2. 默认格式（Controller.methodName）
 */
@Aspect
@RequiredArgsConstructor
public class LogAspect {

    private final LogXClient logXClient;
    private final LogXProperties properties;
    private final UserContextProvider userContextProvider;
    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);

    /**
     * Controller 切入点
     */
    @Pointcut("@within(org.springframework.web.bind.annotation.RestController) || " +
              "@within(org.springframework.stereotype.Controller)")
    public void controllerPointcut() {
    }

    /**
     * Service 切入点
     */
    @Pointcut("@within(org.springframework.stereotype.Service)")
    public void servicePointcut() {
    }

    /**
     * Controller 日志收集
     */
    @Around("controllerPointcut()")
    public Object aroundController(ProceedingJoinPoint joinPoint) throws Throwable {
        // 条件判断：任一条件不满足则跳过
        if (!properties.isEnabled() ||
            !properties.getAspect().isEnabled() ||
            !properties.getAspect().isController()) {
            return joinPoint.proceed();
        }

        // 检查是否有 @LogIgnore 注解
        if (shouldIgnore(joinPoint)) {
            return joinPoint.proceed();
        }

        return logMethod(joinPoint, "Controller");
    }

    /**
     * Service 日志收集
     */
    @Around("servicePointcut()")
    public Object aroundService(ProceedingJoinPoint joinPoint) throws Throwable {
        // 条件判断：任一条件不满足则跳过
        if (!properties.isEnabled() ||
            !properties.getAspect().isEnabled() ||
            !properties.getAspect().isService()) {
            return joinPoint.proceed();
        }

        // 检查是否有 @LogIgnore 注解
        if (shouldIgnore(joinPoint)) {
            return joinPoint.proceed();
        }

        return logMethod(joinPoint, "Service");
    }

    /**
     * 检查是否应该忽略日志记录
     */
    private boolean shouldIgnore(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget().getClass();

        // 检查方法级别的 @LogIgnore
        LogIgnore methodIgnore = AnnotationUtils.findAnnotation(method, LogIgnore.class);
        if (methodIgnore != null) {
            return true;
        }

        // 检查类级别的 @LogIgnore
        LogIgnore classIgnore = AnnotationUtils.findAnnotation(targetClass, LogIgnore.class);
        return classIgnore != null;
    }

    /**
     * 获取模块名称
     * 优先级：方法注解 > 类注解 > 类名映射 > 包名映射 > 默认提取
     */
    private String resolveModuleName(Method method, Class<?> targetClass, String className) {
        // 1. 检查方法级 @LogModule
        LogModule methodModule = AnnotationUtils.findAnnotation(method, LogModule.class);
        if (methodModule != null && !methodModule.value().isEmpty()) {
            return methodModule.value();
        }

        // 2. 检查类级 @LogModule
        LogModule classModule = AnnotationUtils.findAnnotation(targetClass, LogModule.class);
        if (classModule != null && !classModule.value().isEmpty()) {
            return classModule.value();
        }

        // 3. 检查配置文件映射
        if (properties.getModule().isEnabled()) {
            // 3.1 类名映射
            String classMappedModule = properties.getModule().getClassMapping().get(className);
            if (classMappedModule != null && !classMappedModule.isEmpty()) {
                return classMappedModule;
            }

            // 3.2 包名映射（找最长匹配）
            String packageMappedModule = findPackageMapping(className);
            if (packageMappedModule != null) {
                return packageMappedModule;
            }
        }

        // 4. 默认提取
        return extractModuleName(className);
    }

    /**
     * 获取操作名称
     */
    private String resolveOperationName(Method method, String type, String methodName) {
        // 检查 @LogOperation 注解
        LogOperation operation = AnnotationUtils.findAnnotation(method, LogOperation.class);
        if (operation != null && !operation.value().isEmpty()) {
            return operation.value();
        }

        // 默认格式：Controller.methodName
        return type + "." + methodName;
    }

    /**
     * 获取操作配置
     */
    private LogOperationConfig resolveOperationConfig(Method method) {
        LogOperation operation = AnnotationUtils.findAnnotation(method, LogOperation.class);

        LogOperationConfig config = new LogOperationConfig();

        if (operation != null) {
            // 使用注解配置
            config.recordArgs = operation.recordArgs();
            config.recordResult = operation.recordResult();
            config.level = operation.level().isEmpty() ? null : operation.level();
            config.tags = operation.tags().length > 0 ? Arrays.asList(operation.tags()) : null;
        } else {
            // 使用全局配置
            config.recordArgs = properties.getAspect().isLogArgs();
            config.recordResult = properties.getAspect().isLogResult();
            config.level = null;
            config.tags = null;
        }

        return config;
    }

    /**
     * 操作配置内部类
     */
    private static class LogOperationConfig {
        boolean recordArgs;
        boolean recordResult;
        String level;
        List<String> tags;
    }

    /**
     * 从包名映射中查找匹配的模块（最长前缀匹配）
     */
    private String findPackageMapping(String className) {
        Map<String, String> packageMapping = properties.getModule().getPackageMapping();
        if (packageMapping.isEmpty()) {
            return null;
        }

        String bestMatch = null;
        int bestMatchLength = 0;

        for (Map.Entry<String, String> entry : packageMapping.entrySet()) {
            String packagePrefix = entry.getKey();
            if (className.startsWith(packagePrefix) && packagePrefix.length() > bestMatchLength) {
                bestMatch = entry.getValue();
                bestMatchLength = packagePrefix.length();
            }
        }

        return bestMatch;
    }

    private Object logMethod(ProceedingJoinPoint joinPoint, String type) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget().getClass();
        String className = targetClass.getName();
        String methodName = method.getName();

        // 获取 HTTP 请求
        HttpServletRequest request = getRequest();

        // 解析模块和操作名称
        String moduleName = resolveModuleName(method, targetClass, className);
        String operationName = resolveOperationName(method, type, methodName);
        LogOperationConfig opConfig = resolveOperationConfig(method);

        // 构建日志实体
        LogEntry.LogEntryBuilder entryBuilder = LogEntry.builder()
                .module(moduleName)
                .operation(operationName)
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

        // 记录参数（根据配置）
        if (opConfig.recordArgs) {
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

            // 添加自定义标签
            if (opConfig.tags != null) {
                logEntry.setTags(new ArrayList<>(opConfig.tags));
            }

            logXClient.log(logEntry);

            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            context.put("duration", duration + "ms");

            // 设置响应时间
            entryBuilder.responseTime(duration);

            // 记录结果
            if (success) {
                // 记录返回结果（根据配置）
                if (opConfig.recordResult && result != null) {
                    String resultStr = result.toString();
                    context.put("result", resultStr.length() > 200 ?
                            resultStr.substring(0, 200) + "..." : resultStr);
                }

                // 确定日志级别
                String level;
                if (opConfig.level != null) {
                    // 使用注解指定的级别
                    level = opConfig.level;
                } else if (duration > properties.getAspect().getSlowThreshold()) {
                    // 慢请求使用 WARN
                    level = "WARN";
                } else {
                    // 默认 INFO
                    level = "INFO";
                }

                // 构建日志消息
                String message;
                if (duration > properties.getAspect().getSlowThreshold()) {
                    message = String.format("%s 慢请求: %s.%s 耗时 %dms", type, className, methodName, duration);
                } else {
                    message = String.format("%s 执行: %s.%s", type, className, methodName);
                }

                logEntry = entryBuilder
                        .level(level)
                        .message(message)
                        .context(context)
                        .build();

                // 添加标签
                List<String> tags = new ArrayList<>();
                if (opConfig.tags != null) {
                    tags.addAll(opConfig.tags);
                }
                if (duration > properties.getAspect().getSlowThreshold()) {
                    tags.add("slow-request");
                }
                tags.add(type.toLowerCase());
                logEntry.setTags(tags);

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
     * 从类名提取模块名（默认逻辑）
     */
    private String extractModuleName(String className) {
        // 检查是否使用简化类名
        if (properties.getModule().isUseSimpleClassName()) {
            // OrderController -> order
            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            return simpleName
                    .replaceAll("Controller$", "")
                    .replaceAll("Service$", "")
                    .replaceAll("Impl$", "")
                    .toLowerCase();
        }

        // 尝试从包名提取模块名
        // 例如: com.example.user.controller.UserController -> user
        String[] parts = className.split("\\.");
        if (parts.length >= 3) {
            // 倒数第二个部分通常是模块名或层级名
            String modulePart = parts[parts.length - 2];
            // 如果是 controller, service 等，再往前找一个
            if (modulePart.equalsIgnoreCase("controller") ||
                modulePart.equalsIgnoreCase("service") ||
                modulePart.equalsIgnoreCase("api") ||
                modulePart.equalsIgnoreCase("rest") ||
                modulePart.equalsIgnoreCase("web")) {
                if (parts.length >= 4) {
                    return parts[parts.length - 3];
                }
            }
            return modulePart;
        }

        // 无法确定时返回默认模块名
        return properties.getModule().getDefaultModule();
    }
}