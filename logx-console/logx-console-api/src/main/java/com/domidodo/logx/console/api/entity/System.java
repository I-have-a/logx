package com.domidodo.logx.console.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统实体
 */
@Data
@TableName("sys_system")
public class System implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 系统ID
     */
    private String systemId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 系统名称
     */
    private String systemName;

    /**
     * 系统类型
     */
    private String systemType;

    /**
     * API密钥
     */
    private String apiKey;

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
}