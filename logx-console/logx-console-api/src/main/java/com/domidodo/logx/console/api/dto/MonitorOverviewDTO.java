package com.domidodo.logx.console.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 监控总览DTO
 */
@Data
public class MonitorOverviewDTO {
    /**
     * 接入系统总数
     */
    private Integer totalSystems;

    /**
     * 本月新增系统数
     */
    private Integer newSystemsThisMonth;

    /**
     * 今日总日志量
     */
    private Long todayLogCount;

    /**
     * 日志量同比昨日增长率（%）
     */
    private Double logCountGrowthRate;

    /**
     * 异常系统数量
     */
    private Integer abnormalSystemCount;

    /**
     * 系统平均健康度（%）
     */
    private Double avgHealthScore;

    /**
     * 健康度同比昨日增长率（%）
     */
    private Double healthScoreGrowthRate;

    /**
     * 客户系统状态列表
     */
    private List<SystemStatusDTO> systemStatusList;
}
