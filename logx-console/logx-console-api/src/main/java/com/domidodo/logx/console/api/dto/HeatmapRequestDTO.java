package com.domidodo.logx.console.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.util.List;

/**
 * 热力图查询请求DTO
 */
@Data
@Schema(description = "热力图查询请求")
public class HeatmapRequestDTO {

    @Schema(description = "租户ID（可选，不传则查询所有租户）")
    private String tenantId;

    @Schema(description = "系统ID列表（可选，不传则查询所有系统）")
    private List<String> systemIds;

    @Schema(description = "查询日期，默认今天", example = "2026-01-06")
    private LocalDate date;

    @Schema(description = "热度类型：CALL_COUNT(调用量)、ERROR_COUNT(异常量)、" +
                          "RESPONSE_TIME(响应时间)、USER_COUNT(用户数)", example = "CALL_COUNT")
    private HeatType heatType = HeatType.CALL_COUNT;

    @Schema(description = "模块数量限制", example = "20")
    @Min(1)
    @Max(50)
    private Integer moduleLimit = 20;

    @Schema(description = "最小热度阈值（0-100），低于此值不显示", example = "0")
    @Min(0)
    @Max(100)
    private Integer minHeatThreshold = 0;

    @Schema(description = "聚合粒度：HOUR(按小时)、HALF_HOUR(按半小时)", example = "HOUR")
    private Granularity granularity = Granularity.HOUR;

    /**
     * 热度类型枚举
     */
    public enum HeatType {
        /**
         * 调用量
         */
        CALL_COUNT,
        /**
         * 异常量
         */
        ERROR_COUNT,
        /**
         * 平均响应时间
         */
        RESPONSE_TIME,
        /**
         * 活跃用户数
         */
        USER_COUNT
    }

    /**
     * 时间粒度
     */
    public enum Granularity {
        /**
         * 按小时
         */
        HOUR,
        /**
         * 按半小时
         */
        HALF_HOUR
    }
}