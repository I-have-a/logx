package com.domidodo.logx.sdk.spring.annotation;

import java.lang.annotation.*;

/**
 * 日志模块注解
 * 用于自定义接口所属的模块名称
 * <p>
 * 优先级：方法级别 > 类级别 > 配置映射 > 默认提取
 * <p>
 * 使用示例：
 * <pre>
 * // 类级别 - 该类下所有方法默认使用此模块名
 * {@code @LogModule("订单管理")}
 * {@code @RestController}
 * public class OrderController {
 *
 *     // 方法级别 - 覆盖类级别配置
 *     {@code @LogModule("订单查询")}
 *     {@code @GetMapping("/list")}
 *     public List<Order> list() { ... }
 *
 *     // 未标注则使用类级别的"订单管理"
 *     {@code @PostMapping("/create")}
 *     public Order create() { ... }
 * }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogModule {

    /**
     * 模块名称
     */
    String value();

    /**
     * 模块描述（可选）
     */
    String description() default "";
}