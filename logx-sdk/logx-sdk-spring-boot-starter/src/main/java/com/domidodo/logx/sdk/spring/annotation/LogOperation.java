package com.domidodo.logx.sdk.spring.annotation;

import java.lang.annotation.*;

/**
 * 日志操作注解
 * 用于自定义接口的操作名称和其他日志属性
 * <p>
 * 使用示例：
 * <pre>
 * {@code @RestController}
 * {@code @LogModule("用户管理")}
 * public class UserController {
 *
 *     {@code @LogOperation(value = "用户登录", recordArgs = false)}
 *     {@code @PostMapping("/login")}
 *     public Result login(LoginRequest request) { ... }
 *
 *     {@code @LogOperation(value = "查询用户列表", tags = {"query", "user"})}
 *     {@code @GetMapping("/list")}
 *     public List<User> list() { ... }
 *
 *     {@code @LogOperation(value = "删除用户", level = "WARN")}
 *     {@code @DeleteMapping("/{id}")}
 *     public void delete(@PathVariable Long id) { ... }
 * }
 * </pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogOperation {

    /**
     * 操作名称
     */
    String value();

    /**
     * 操作描述（可选）
     */
    String description() default "";

    /**
     * 是否记录请求参数
     * 默认跟随全局配置，设为 false 可针对敏感接口关闭
     */
    boolean recordArgs() default true;

    /**
     * 是否记录返回结果
     * 默认跟随全局配置，设为 false 可针对大数据量接口关闭
     */
    boolean recordResult() default true;

    /**
     * 日志级别：DEBUG, INFO, WARN, ERROR
     * 默认为空，跟随全局配置（通常是 INFO）
     */
    String level() default "";

    /**
     * 自定义标签
     */
    String[] tags() default {};
}