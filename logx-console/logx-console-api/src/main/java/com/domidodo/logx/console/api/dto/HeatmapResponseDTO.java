package com.domidodo.logx.console.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 热力图响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "热力图响应数据")
public class HeatmapResponseDTO {

    @Schema(description = "查询日期")
    private LocalDate date;

    @Schema(description = "热度类型")
    private String heatType;

    @Schema(description = "时间粒度")
    private String granularity;

    @Schema(description = "时间轴标签（X轴）", example = "[\"00:00\", \"01:00\", ...]")
    private List<String> timeLabels;

    @Schema(description = "模块列表（Y轴）")
    private List<ModuleInfo> modules;

    @Schema(description = "热力图数据矩阵 [模块索引][时间索引]")
    private List<List<HeatmapCell>> heatmapData;

    @Schema(description = "统计摘要")
    private HeatmapSummary summary;

    @Schema(description = "颜色配置")
    private ColorConfig colorConfig;

    /**
     * 模块信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "模块信息")
    public static class ModuleInfo {

        @Schema(description = "模块名称")
        private String moduleName;

        @Schema(description = "所属系统ID")
        private String systemId;

        @Schema(description = "所属系统名称")
        private String systemName;

        @Schema(description = "所属租户ID")
        private String tenantId;

        @Schema(description = "模块总调用量")
        private Long totalCount;

        @Schema(description = "模块总异常量")
        private Long errorCount;

        @Schema(description = "模块平均响应时间(ms)")
        private Double avgResponseTime;

        @Schema(description = "活跃用户数")
        private Long userCount;

        @Schema(description = "峰值时段（小时）")
        private Integer peakHour;

        @Schema(description = "峰值数值")
        private Long peakValue;
    }

    /**
     * 热力图单元格
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "热力图单元格数据")
    public static class HeatmapCell {

        @Schema(description = "时间段索引")
        private Integer timeIndex;

        @Schema(description = "时间段标签", example = "09:00-10:00")
        private String timeLabel;

        @Schema(description = "原始数值")
        private Long value;

        @Schema(description = "热度值（0-100）")
        private Integer heat;

        @Schema(description = "颜色值（十六进制）", example = "#FF5733")
        private String color;

        @Schema(description = "调用量")
        private Long callCount;

        @Schema(description = "异常量")
        private Long errorCount;

        @Schema(description = "平均响应时间(ms)")
        private Double avgResponseTime;

        @Schema(description = "用户数")
        private Long userCount;

        @Schema(description = "异常率(%)")
        private Double errorRate;
    }

    /**
     * 统计摘要
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "热力图统计摘要")
    public static class HeatmapSummary {

        @Schema(description = "总模块数")
        private Integer totalModules;

        @Schema(description = "总调用量")
        private Long totalCallCount;

        @Schema(description = "总异常量")
        private Long totalErrorCount;

        @Schema(description = "整体异常率(%)")
        private Double overallErrorRate;

        @Schema(description = "平均响应时间(ms)")
        private Double avgResponseTime;

        @Schema(description = "峰值时段")
        private String peakHour;

        @Schema(description = "峰值调用量")
        private Long peakCallCount;

        @Schema(description = "低谷时段")
        private String valleyHour;

        @Schema(description = "低谷调用量")
        private Long valleyCallCount;

        @Schema(description = "最活跃模块")
        private String mostActiveModule;

        @Schema(description = "最高异常率模块")
        private String highestErrorRateModule;

        @Schema(description = "按小时统计的调用量")
        private Map<Integer, Long> hourlyCallCounts;

        @Schema(description = "按小时统计的异常量")
        private Map<Integer, Long> hourlyErrorCounts;
    }

    /**
     * 颜色配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "热力图颜色配置")
    public static class ColorConfig {

        @Schema(description = "最低热度颜色", example = "#E8F5E9")
        private String minColor;

        @Schema(description = "最高热度颜色", example = "#C62828")
        private String maxColor;

        @Schema(description = "颜色梯度（从低到高）")
        private List<String> gradient;

        @Schema(description = "无数据颜色", example = "#F5F5F5")
        private String noDataColor;

        @Schema(description = "热度阈值说明")
        private List<ThresholdLabel> thresholds;
    }

    /**
     * 阈值标签
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "热度阈值标签")
    public static class ThresholdLabel {

        @Schema(description = "阈值下限")
        private Integer min;

        @Schema(description = "阈值上限")
        private Integer max;

        @Schema(description = "标签", example = "低")
        private String label;

        @Schema(description = "颜色")
        private String color;
    }
}