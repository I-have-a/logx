package com.domidodo.logx.console.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模块统计 DTO
 */
@Data
public class ModuleStatsDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 排名
     */
    private Integer rank;

    /**
     * 模块名称
     */
    private String moduleName;

    /**
     * 模块分类
     */
    private String category;

    /**
     * 调用次数
     */
    private Long callCount;

    /**
     * 使用用户数
     */
    private Long userCount;

    /**
     * 平均响应时间(ms)
     */
    private Long avgResponseTime;

    /**
     * 异常率(%)
     */
    private Double errorRate;

    /**
     * 趋势（增长百分比）
     */
    private String trend;
}