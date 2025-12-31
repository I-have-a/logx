package com.domidodo.logx.sdk.spring.annotation;

import java.lang.annotation.*;

/**
 * 日志忽略注解
 * 标注此注解的方法或类将不会被 LogX 记录日志
 *
 * 使用示例：
 * <pre>
 * {@code @RestController}
 * public class HealthController {
 *
 *     // 健康检查接口，不需要记录日志
 *     {@code @LogIgnore}
 *     {@code @GetMapping("/health")}
 *     public String health() {
 *         return "OK";
 *     }
 * }
 *
 * // 整个类都不记录日志
 * {@code @LogIgnore}
 * {@code @RestController}
 * public class InternalController {
 *     // ...
 * }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogIgnore {

    /**
     * 忽略原因（可选，用于文档说明）
     */
    String reason() default "";
}