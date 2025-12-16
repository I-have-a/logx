package com.domidodo.logx.common.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统信息DTO
 */
@Data
public class SystemDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
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
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 租户名称（关联查询）
     */
    private String tenantName;

    /**
     * 今日日志量
     */
    private Long todayLogCount;

    /**
     * 异常率
     */
    private Double errorRate;

    /**
     * 健康度
     */
    private Double healthScore;
}