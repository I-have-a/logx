package com.domidodo.logx.console.api.dto;


import lombok.Data;

/**
 * 健康因素
 */
@Data
public class HealthFactor {
    private String factor;      // 因素名称
    private String impact;      // 影响程度：HIGH/MEDIUM/LOW
    private String suggestion;  // 优化建议
}
