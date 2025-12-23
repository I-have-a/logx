package com.domidodo.logx.console.api.dto;


import lombok.Data;

/**
 * 健康度分布
 */
@Data
public class HealthDistribution {
    private Integer excellent; // >= 95%
    private Integer good;      // 85-95%
    private Integer fair;      // 75-85%
    private Integer poor;      // 60-75%
    private Integer critical;  // < 60%
}