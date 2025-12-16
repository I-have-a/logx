package com.domidodo.logx.console.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统计结果 DTO
 */
@Data
public class StatisticsDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 今日操作总数
     */
    private Long todayTotal;

    /**
     * 今日异常数
     */
    private Long todayErrors;

    /**
     * 活跃用户数
     */
    private Long activeUsers;

    /**
     * 平均响应时间
     */
    private Long avgResponseTime;

    /**
     * 昨日操作总数
     */
    private Long yesterdayTotal;

    /**
     * 昨日异常数
     */
    private Long yesterdayErrors;

    /**
     * 昨日活跃用户数
     */
    private Long yesterdayActiveUsers;

    /**
     * 昨日平均响应时间
     */
    private Long yesterdayAvgResponseTime;

    /**
     * 同比增长率
     */
    private Double totalGrowthRate;

    /**
     * 异常同比增长率
     */
    private Double errorGrowthRate;

    /**
     * 用户同比增长率
     */
    private Double userGrowthRate;

    /**
     * 响应时间变化（ms）
     */
    private Long responseTimeChange;
}