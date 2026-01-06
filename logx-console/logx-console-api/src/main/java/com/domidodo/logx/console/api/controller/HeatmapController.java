package com.domidodo.logx.console.api.controller;

import com.domidodo.logx.common.result.Result;
import com.domidodo.logx.console.api.dto.HeatmapRequestDTO;
import com.domidodo.logx.console.api.dto.HeatmapResponseDTO;
import com.domidodo.logx.console.api.service.HeatmapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 热力图控制器
 * 提供24小时模块调用热力图分析接口
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis/heatmap")
@RequiredArgsConstructor
@Tag(name = "热力图分析", description = "24小时模块调用热力图分析接口")
public class HeatmapController {

    private final HeatmapService heatmapService;

    /**
     * 获取24小时模块热力图
     */
    @PostMapping("/module")
    @Operation(summary = "获取24小时模块热力图",
            description = "根据租户、系统、日期等条件生成模块调用热力图")
    public Result<HeatmapResponseDTO> getModuleHeatmap(
            @Validated @RequestBody HeatmapRequestDTO request) {
        try {
            log.info("热力图查询请求: tenantId={}, systemIds={}, date={}, heatType={}",
                    request.getTenantId(),
                    request.getSystemIds(),
                    request.getDate(),
                    request.getHeatType());

            HeatmapResponseDTO response = heatmapService.getModuleHeatmap(request);

            log.info("热力图查询完成: 模块数={}, 总调用量={}",
                    response.getModules().size(),
                    response.getSummary().getTotalCallCount());

            return Result.success(response);

        } catch (IllegalArgumentException e) {
            log.warn("热力图参数错误: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("热力图查询失败", e);
            return Result.error("查询失败，请稍后重试");
        }
    }

    /**
     * 获取24小时模块热力图（GET简化版）
     */
    @GetMapping("/module")
    @Operation(summary = "获取24小时模块热力图（简化版）",
            description = "通过URL参数快速查询热力图")
    public Result<HeatmapResponseDTO> getModuleHeatmapSimple(
            @Parameter(description = "租户ID")
            @RequestParam(required = false) String tenantId,

            @Parameter(description = "系统ID列表，逗号分隔")
            @RequestParam(required = false) List<String> systemIds,

            @Parameter(description = "查询日期，默认今天")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,

            @Parameter(description = "热度类型：CALL_COUNT/ERROR_COUNT/RESPONSE_TIME/USER_COUNT")
            @RequestParam(defaultValue = "CALL_COUNT") String heatType,

            @Parameter(description = "模块数量限制")
            @RequestParam(defaultValue = "20") Integer moduleLimit,

            @Parameter(description = "最小热度阈值(0-100)")
            @RequestParam(defaultValue = "0") Integer minHeatThreshold,

            @Parameter(description = "时间粒度：HOUR/HALF_HOUR")
            @RequestParam(defaultValue = "HOUR") String granularity) {

        try {
            // 构建请求对象
            HeatmapRequestDTO request = new HeatmapRequestDTO();
            request.setTenantId(tenantId);
            request.setSystemIds(systemIds);
            request.setDate(date);
            request.setModuleLimit(moduleLimit);
            request.setMinHeatThreshold(minHeatThreshold);

            // 解析枚举
            try {
                request.setHeatType(HeatmapRequestDTO.HeatType.valueOf(heatType.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return Result.error(400, "无效的热度类型: " + heatType);
            }

            try {
                request.setGranularity(HeatmapRequestDTO.Granularity.valueOf(granularity.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return Result.error(400, "无效的时间粒度: " + granularity);
            }

            HeatmapResponseDTO response = heatmapService.getModuleHeatmap(request);
            return Result.success(response);

        } catch (Exception e) {
            log.error("热力图查询失败", e);
            return Result.error("查询失败，请稍后重试");
        }
    }

    /**
     * 获取热力图颜色配置
     */
    @GetMapping("/color-config")
    @Operation(summary = "获取热力图颜色配置",
            description = "返回热力图的颜色梯度和阈值配置")
    public Result<HeatmapResponseDTO.ColorConfig> getColorConfig() {
        try {
            // 返回默认颜色配置
            HeatmapResponseDTO.ColorConfig config = HeatmapResponseDTO.ColorConfig.builder()
                    .minColor("#E8F5E9")
                    .maxColor("#C62828")
                    .noDataColor("#F5F5F5")
                    .gradient(List.of(
                            "#E8F5E9", "#C8E6C9", "#A5D6A7", "#81C784",
                            "#FFF9C4", "#FFE082", "#FFB74D", "#FF8A65",
                            "#E57373", "#C62828"
                    ))
                    .thresholds(List.of(
                            HeatmapResponseDTO.ThresholdLabel.builder()
                                    .min(0).max(20).label("低").color("#C8E6C9").build(),
                            HeatmapResponseDTO.ThresholdLabel.builder()
                                    .min(20).max(40).label("较低").color("#81C784").build(),
                            HeatmapResponseDTO.ThresholdLabel.builder()
                                    .min(40).max(60).label("中等").color("#FFE082").build(),
                            HeatmapResponseDTO.ThresholdLabel.builder()
                                    .min(60).max(80).label("较高").color("#FF8A65").build(),
                            HeatmapResponseDTO.ThresholdLabel.builder()
                                    .min(80).max(100).label("高").color("#C62828").build()
                    ))
                    .build();

            return Result.success(config);

        } catch (Exception e) {
            log.error("获取颜色配置失败", e);
            return Result.error("获取配置失败");
        }
    }

    /**
     * 获取热度类型列表
     */
    @GetMapping("/heat-types")
    @Operation(summary = "获取热度类型列表",
            description = "返回可用的热度类型选项")
    public Result<List<HeatTypeOption>> getHeatTypes() {
        List<HeatTypeOption> options = List.of(
                new HeatTypeOption("CALL_COUNT", "调用量", "统计模块被调用的次数"),
                new HeatTypeOption("ERROR_COUNT", "异常量", "统计模块产生的异常数量"),
                new HeatTypeOption("RESPONSE_TIME", "响应时间", "统计模块的平均响应时间(ms)"),
                new HeatTypeOption("USER_COUNT", "用户数", "统计访问模块的独立用户数")
        );
        return Result.success(options);
    }

    /**
     * 热度类型选项
     */
    public record HeatTypeOption(
            String value,
            String label,
            String description
    ) {}
}