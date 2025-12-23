package com.domidodo.logx.console.api.controller;

import com.domidodo.logx.common.result.Result;
import com.domidodo.logx.console.api.dto.ModuleStatsDTO;
import com.domidodo.logx.console.api.dto.StatisticsDTO;
import com.domidodo.logx.console.api.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统计分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Tag(name = "统计分析", description = "统计分析相关接口")
public class AnalysisController {

    private final AnalysisService analysisService;

    /**
     * 获取今日统计
     */
    @GetMapping("/today")
    @Operation(summary = "获取今日统计")
    public Result<StatisticsDTO> getTodayStatistics(
            @RequestParam String tenantId,
            @RequestParam String systemId) {
        try {
            StatisticsDTO stats = analysisService.getTodayStatistics(tenantId, systemId);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取今天的统计数据失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取模块使用统计
     */
    @GetMapping("/modules")
    @Operation(summary = "获取模块使用统计")
    public Result<List<ModuleStatsDTO>> getModuleStatistics(
            @RequestParam String tenantId,
            @RequestParam String systemId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "callCount") String sortBy,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            List<ModuleStatsDTO> stats = analysisService.getModuleStatistics(
                    tenantId, systemId, startTime, endTime, sortBy, limit);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取模块统计信息失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取热点功能分析
     */
    @GetMapping("/hotspots")
    @Operation(summary = "获取热点功能分析")
    public Result<List<Map<String, Object>>> getHotspotAnalysis(
            @RequestParam String tenantId,
            @RequestParam String systemId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            List<Map<String, Object>> hotspots = analysisService.getHotspotAnalysis(
                    tenantId, systemId, startTime, endTime);
            return Result.success(hotspots);
        } catch (Exception e) {
            log.error("获取热点分析失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取异常趋势
     */
    @GetMapping("/exception-trend")
    @Operation(summary = "获取异常趋势")
    public Result<Map<String, Object>> getExceptionTrend(
            @RequestParam String tenantId,
            @RequestParam String systemId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        // TODO: 实现异常趋势分析
        return Result.error("功能开发中");
    }

    /**
     * 获取用户行为分析
     */
    @GetMapping("/user-behavior")
    @Operation(summary = "获取用户行为分析")
    public Result<Map<String, Object>> getUserBehavior(
            @RequestParam String tenantId,
            @RequestParam String systemId,
            @RequestParam(required = false) String userId) {
        // TODO: 实现用户行为分析
        return Result.error("功能开发中");
    }
}