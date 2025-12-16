package com.domidodo.logx.engine.detection.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警记录实体
 */
@Data
@TableName("log_alert_record")
public class Alert implements Serializable {
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
     * 规则ID
     */
    private Long ruleId;

    /**
     * 告警级别：CRITICAL/WARNING/INFO
     */
    private String alertLevel;

    /**
     * 告警类型
     */
    private String alertType;

    /**
     * 告警内容
     */
    private String alertContent;

    /**
     * 触发时间
     */
    private LocalDateTime triggerTime;

    /**
     * 处理状态：PENDING/PROCESSING/RESOLVED
     */
    private String status;

    /**
     * 处理人
     */
    private String handleUser;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;

    /**
     * 处理备注
     */
    private String handleRemark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}