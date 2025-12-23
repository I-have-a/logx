package com.domidodo.logx.console.api.dto;

import lombok.Data;

/**
 * 异常趋势点
 */
@Data
public class ExceptionTrendPoint {
    private String date;
    private Long totalCount;
    private Long criticalCount;
    private Long warningCount;
    private Long infoCount;
}
