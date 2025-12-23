package com.domidodo.logx.console.api.dto;


import lombok.Data;

import java.util.List;

/**
 * 系统健康度详情DTO
 */
@Data
public class SystemHealthDTO {
    /**
     * 系统ID
     */
    private String systemId;

    /**
     * 系统名称
     */
    private String systemName;

    /**
     * 综合健康度（%）
     */
    private Double overallHealthScore;

    /**
     * 健康等级
     */
    private String healthLevel;

    /**
     * 可用性得分（%）
     */
    private Double availabilityScore;

    /**
     * 性能得分（%）
     */
    private Double performanceScore;

    /**
     * 异常率得分（%）
     */
    private Double exceptionScore;

    /**
     * 资源使用得分（%）
     */
    private Double resourceScore;

    /**
     * 总请求数
     */
    private Long totalRequests;

    /**
     * 失败请求数
     */
    private Long failedRequests;

    /**
     * 异常日志数
     */
    private Long exceptionLogCount;

    /**
     * 平均响应时间（ms）
     */
    private Double avgResponseTime;

    /**
     * P99响应时间（ms）
     */
    private Double p99ResponseTime;

    /**
     * 统计时间范围
     */
    private String timeRange;

    /**
     * 健康度趋势（最近7天）
     */
    private List<HealthTrendPoint> healthTrend;
}
