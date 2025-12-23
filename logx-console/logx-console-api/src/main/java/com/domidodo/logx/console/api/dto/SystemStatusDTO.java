package com.domidodo.logx.console.api.dto;

import lombok.Data;

/**
 * 系统状态DTO
 */
@Data
public class SystemStatusDTO {
    /**
     * 系统ID
     */
    private String systemId;

    /**
     * 系统名称
     */
    private String systemName;

    /**
     * 租户名称
     */
    private String tenantName;

    /**
     * 今日日志量
     */
    private Long todayLogCount;

    /**
     * 异常率（%）
     */
    private Double exceptionRate;

    /**
     * 健康度（%）
     */
    private Double healthScore;

    /**
     * SLA达成率（%）
     */
    private Double slaAchievementRate;

    /**
     * 健康等级：优秀/良好/一般/较差/危险
     */
    private String healthLevel;
}
