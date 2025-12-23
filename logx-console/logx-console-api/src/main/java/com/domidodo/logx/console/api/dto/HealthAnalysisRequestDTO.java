package com.domidodo.logx.console.api.dto;

import lombok.Data;

/**
 * 健康度分析请求DTO
 */
@Data
public class HealthAnalysisRequestDTO {
    /**
     * 系统ID（可选，不传则分析所有系统）
     */
    private String systemId;

    /**
     * 分析时间范围（小时）
     */
    private Integer timeRangeHours = 24;

    /**
     * 是否包含趋势分析
     */
    private Boolean includeTrend = true;
}
