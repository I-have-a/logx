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
     * 监控指标："responseTime" / "userId" / "level" 等任意字段
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

    // ==================== 静默期相关字段（新增） ====================

    /**
     * 静默时长（秒）
     * 在此时间内同一规则不会重复告警
     * 默认300秒（5分钟）
     */
    private Integer silencePeriod;

    /**
     * 静默粒度：
     * - RULE: 规则级别（同一规则全局静默）
     * - TARGET: 目标级别（同一规则的同一监控目标静默）
     * - USER: 用户级别（同一规则的同一用户静默）
     */
    private String silenceScope;

    /**
     * 是否允许升级突破静默期
     * 当新告警级别高于上次时，即使在静默期内也发送告警
     */
    private Boolean allowEscalation;

    /**
     * 是否启用告警聚合
     * 静默期结束后发送聚合摘要
     */
    private Boolean enableAggregation;

    // ==================== 触发统计字段 ====================

    /**
     * 触发次数
     */
    private Integer triggerCount;

    /**
     * 最后触发时间
     */
    private LocalDateTime lastTriggerTime;

    // ==================== 时间戳字段 ====================

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

    // ==================== 辅助方法 ====================

    /**
     * 获取有效的静默时长（秒）
     * 如果未配置则返回默认值300秒
     */
    public int getEffectiveSilencePeriod() {
        return silencePeriod != null && silencePeriod > 0 ? silencePeriod : 300;
    }

    /**
     * 获取有效的静默粒度
     * 如果未配置则返回默认值 RULE
     */
    public String getEffectiveSilenceScope() {
        return silenceScope != null && !silenceScope.isEmpty() ? silenceScope : "RULE";
    }

    /**
     * 是否允许升级突破静默期
     */
    public boolean isAllowEscalation() {
        return allowEscalation != null && allowEscalation;
    }

    /**
     * 是否启用告警聚合
     */
    public boolean isEnableAggregation() {
        return enableAggregation != null && enableAggregation;
    }
}