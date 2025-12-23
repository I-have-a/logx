package com.domidodo.logx.console.api.dto;

import lombok.Data;

/**
 * 健康度趋势点
 */
@Data
public class HealthTrendPoint {
    /**
     * 日期
     */
    private String date;

    /**
     * 健康度（%）
     */
    private Double healthScore;

    /**
     * 日志量
     */
    private Long logCount;

    /**
     * 异常数
     */
    private Long exceptionCount;
}
