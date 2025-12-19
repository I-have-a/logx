package com.domidodo.logx.engine.detection.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 异常规则实体（Detection 模块使用）
 */
@Data
@TableName("log_exception_rule")
public class Rule implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 系统ID
     */
    private String systemId;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 规则类型：RESPONSE_TIME/ERROR_RATE/CONTINUOUS_FAILURE/FREQUENT_OPERATION
     */
    private String ruleType;

    /**
     * 监控对象（模块/接口/用户）
     */
    private String monitorTarget;

    /**
     * 监控指标:"responseTime" / "userId" / "level" 等任意字段
     */
    private String monitorMetric;

    /**
     * 条件操作符：">", "<", ">=", "<=", "=", "!=", "contains", "startsWith"
     */
    private String conditionOperator;

    /**
     * 条件值
     */
    private String conditionValue;

    /**
     * 告警级别：CRITICAL/WARNING/INFO
     */
    private String alertLevel;

    /**
     * 状态：0=禁用，1=启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 触发条件字段
     */
    private String conditionField;
}