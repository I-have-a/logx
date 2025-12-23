package com.domidodo.logx.console.api.dto;


import lombok.Data;

import java.util.List;

/**
 * 全局异常监控DTO
 */
@Data
public class GlobalExceptionMonitorDTO {
    /**
     * 异常总数
     */
    private Long totalExceptions;

    /**
     * 今日新增异常数
     */
    private Long todayNewExceptions;

    /**
     * 待处理异常数
     */
    private Long pendingExceptions;

    /**
     * 按系统分组的异常统计
     */
    private List<SystemExceptionStats> systemStats;

    /**
     * 按类型分组的异常统计
     */
    private List<ExceptionTypeStats> typeStats;

    /**
     * 高频异常列表（TOP 10）
     */
    private List<FrequentExceptionDTO> frequentExceptions;

    /**
     * 异常趋势（最近7天）
     */
    private List<ExceptionTrendPoint> exceptionTrend;
}

