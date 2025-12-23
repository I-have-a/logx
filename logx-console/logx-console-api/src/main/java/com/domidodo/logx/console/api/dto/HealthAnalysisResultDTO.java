package com.domidodo.logx.console.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 健康度分析响应DTO
 */
@Data
public class HealthAnalysisResultDTO {
    /**
     * 总体健康度
     */
    private Double overallHealth;

    /**
     * 健康系统数
     */
    private Integer healthySystemCount;

    /**
     * 警告系统数
     */
    private Integer warningSystemCount;

    /**
     * 危险系统数
     */
    private Integer criticalSystemCount;

    /**
     * 各系统健康度详情
     */
    private List<SystemHealthDTO> systemHealthDetails;

    /**
     * 健康度分布
     */
    private HealthDistribution healthDistribution;

    /**
     * 影响健康度的主要因素
     */
    private List<HealthFactor> mainFactors;
}
