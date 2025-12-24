package com.domidodo.logx.console.api.controller;

import com.domidodo.logx.common.result.PageResult;
import com.domidodo.logx.common.result.Result;
import com.domidodo.logx.console.api.dto.*;
import com.domidodo.logx.console.api.service.MonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控后台控制器
 * 提供全局监控、跨系统查询、健康度分析等功能
 */
@Slf4j
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
@Tag(name = "监控后台", description = "全局监控相关接口")
public class MonitorController {

    private final MonitorService monitorService;

    // ==================== 系统总览 ====================

    /**
     * 获取监控总览
     */
    @GetMapping("/overview")
    @Operation(summary = "获取监控总览")
    public Result<MonitorOverviewDTO> getMonitorOverview() {
        try {
            log.info("获取监控总览数据");
            MonitorOverviewDTO overview = monitorService.getMonitorOverview();
            return Result.success(overview);
        } catch (Exception e) {
            log.error("获取监控总览失败", e);
            return Result.error("获取监控总览失败: " + e.getMessage());
        }
    }

    /**
     * 获取系统状态列表（分页）
     */
    @GetMapping("/systems/status")
    @Operation(summary = "获取系统状态列表")
    public Result<PageResult<SystemStatusDTO>> getSystemStatusList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String healthLevel) {
        try {
            log.info("获取系统状态列表: page={}, size={}, keyword={}, healthLevel={}",
                    page, size, keyword, healthLevel);

            PageResult<SystemStatusDTO> result = monitorService.getSystemStatusList(
                    page, size, keyword, healthLevel);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取系统状态列表失败", e);
            return Result.error("获取系统状态列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个系统的详细状态
     */
    @GetMapping("/systems/{systemId}/status")
    @Operation(summary = "获取系统详细状态")
    public Result<SystemStatusDTO> getSystemStatus(@PathVariable String systemId) {
        try {
            log.info("获取系统状态: {}", systemId);
            SystemStatusDTO status = monitorService.getSystemStatus(systemId);
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取系统状态失败: {}", systemId, e);
            return Result.error("获取系统状态失败: " + e.getMessage());
        }
    }

    // ==================== 跨系统日志查询 ====================

    /**
     * 跨系统日志查询
     */
    @PostMapping("/logs/cross-system/query")
    @Operation(summary = "跨系统日志查询")
    public Result<CrossSystemQueryResultDTO> crossSystemQuery(
            @Valid @RequestBody CrossSystemQueryDTO queryDTO) {
        try {
            log.info("跨系统日志查询: systemIds={}, keyword={}, timeRange={} to {}",
                    queryDTO.getSystemIds(), queryDTO.getKeyword(),
                    queryDTO.getStartTime(), queryDTO.getEndTime());

            CrossSystemQueryResultDTO result = monitorService.crossSystemQuery(queryDTO);
            return Result.success(result);
        } catch (Exception e) {
            log.error("跨系统日志查询失败", e);
            return Result.error("跨系统日志查询失败: " + e.getMessage());
        }
    }

    /**
     * 导出跨系统查询结果
     */
    @PostMapping("/logs/cross-system/export")
    @Operation(summary = "导出跨系统查询结果")
    public Result<String> exportCrossSystemLogs(
            @Valid @RequestBody CrossSystemQueryDTO queryDTO) {
        try {
            log.info("导出跨系统日志: systemIds={}", queryDTO.getSystemIds());

            // 限制导出数量
            if (queryDTO.getSize() == null || queryDTO.getSize() > 10000) {
                return Result.error("导出数量不能超过10000条");
            }

            String exportUrl = monitorService.exportCrossSystemLogs(queryDTO);
            return Result.success(exportUrl);
        } catch (Exception e) {
            log.error("导出跨系统日志失败", e);
            return Result.error("导出失败: " + e.getMessage());
        }
    }

    /**
     * 获取可查询的系统列表
     */
    @GetMapping("/systems/queryable")
    @Operation(summary = "获取可查询的系统列表")
    public Result<List<SystemStatusDTO>> getQueryableSystems() {
        try {
            List<SystemStatusDTO> systems = monitorService.getQueryableSystems();
            return Result.success(systems);
        } catch (Exception e) {
            log.error("获取可查询系统列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    // ==================== 全局异常监控 ====================

    /**
     * 获取全局异常监控数据
     */
    @GetMapping("/exceptions/global")
    @Operation(summary = "获取全局异常监控数据")
    public Result<GlobalExceptionMonitorDTO> getGlobalExceptionMonitor(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            log.info("获取全局异常监控: {} to {}", startTime, endTime);

            // 默认查询最近24小时
            if (startTime == null) {
                startTime = LocalDateTime.now().minusHours(24);
            }
            if (endTime == null) {
                endTime = LocalDateTime.now();
            }

            GlobalExceptionMonitorDTO monitor = monitorService.getGlobalExceptionMonitor(
                    startTime, endTime);
            return Result.success(monitor);
        } catch (Exception e) {
            log.error("获取全局异常监控失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取高频异常列表
     */
    @GetMapping("/exceptions/frequent")
    @Operation(summary = "获取高频异常列表")
    public Result<List<FrequentExceptionDTO>> getFrequentExceptions(
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            log.info("获取高频异常: limit={}", limit);

            if (startTime == null) {
                startTime = LocalDateTime.now().minusDays(7);
            }
            if (endTime == null) {
                endTime = LocalDateTime.now();
            }

            List<FrequentExceptionDTO> exceptions = monitorService.getFrequentExceptions(
                    limit, startTime, endTime);
            return Result.success(exceptions);
        } catch (Exception e) {
            log.error("获取高频异常失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取异常趋势
     */
    @GetMapping("/exceptions/trend")
    @Operation(summary = "获取异常趋势")
    public Result<List<ExceptionTrendPoint>> getExceptionTrend(
            @RequestParam(defaultValue = "7") Integer days) {
        try {
            log.info("获取异常趋势: days={}", days);

            if (days < 1 || days > 90) {
                return Result.error("天数范围必须在1-90之间");
            }

            List<ExceptionTrendPoint> trend = monitorService.getExceptionTrend(days);
            return Result.success(trend);
        } catch (Exception e) {
            log.error("获取异常趋势失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 按系统统计异常
     */
    @GetMapping("/exceptions/by-system")
    @Operation(summary = "按系统统计异常")
    public Result<List<SystemExceptionStats>> getExceptionsBySystem(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            log.info("按系统统计异常: {} to {}", startTime, endTime);

            if (startTime == null) {
                startTime = LocalDateTime.now().minusHours(24);
            }
            if (endTime == null) {
                endTime = LocalDateTime.now();
            }

            List<SystemExceptionStats> stats = monitorService.getExceptionsBySystem(
                    startTime, endTime);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("按系统统计异常失败", e);
            return Result.error("统计失败: " + e.getMessage());
        }
    }

    /**
     * 按类型统计异常
     */
    @GetMapping("/exceptions/by-type")
    @Operation(summary = "按类型统计异常")
    public Result<List<ExceptionTypeStats>> getExceptionsByType(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            log.info("按类型统计异常: {} to {}", startTime, endTime);

            if (startTime == null) {
                startTime = LocalDateTime.now().minusHours(24);
            }
            if (endTime == null) {
                endTime = LocalDateTime.now();
            }

            List<ExceptionTypeStats> stats = monitorService.getExceptionsByType(
                    startTime, endTime);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("按类型统计异常失败", e);
            return Result.error("统计失败: " + e.getMessage());
        }
    }

    // ==================== 健康度分析 ====================

    /**
     * 获取全局健康度分析
     */
    @PostMapping("/health/analysis")
    @Operation(summary = "获取健康度分析")
    public Result<HealthAnalysisResultDTO> getHealthAnalysis(
            @Valid @RequestBody HealthAnalysisRequestDTO requestDTO) {
        try {
            log.info("获取健康度分析: systemId={}, timeRange={}h",
                    requestDTO.getSystemId(), requestDTO.getTimeRangeHours());

            HealthAnalysisResultDTO result = monitorService.getHealthAnalysis(requestDTO);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取健康度分析失败", e);
            return Result.error("分析失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个系统健康度
     */
    @GetMapping("/systems/{systemId}/health")
    @Operation(summary = "获取系统健康度")
    public Result<SystemHealthDTO> getSystemHealth(
            @PathVariable String systemId,
            @RequestParam(defaultValue = "24") Integer hours) {
        try {
            log.info("获取系统健康度: systemId={}, hours={}", systemId, hours);

            if (hours < 1 || hours > 720) {
                return Result.error("时间范围必须在1-720小时之间");
            }

            SystemHealthDTO health = monitorService.getSystemHealth(systemId, hours);
            return Result.success(health);
        } catch (Exception e) {
            log.error("获取系统健康度失败: {}", systemId, e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取健康度趋势
     */
    @GetMapping("/systems/{systemId}/health/trend")
    @Operation(summary = "获取健康度趋势")
    public Result<List<HealthTrendPoint>> getHealthTrend(
            @PathVariable String systemId,
            @RequestParam(defaultValue = "7") Integer days) {
        try {
            log.info("获取健康度趋势: systemId={}, days={}", systemId, days);

            if (days < 1 || days > 90) {
                return Result.error("天数范围必须在1-90之间");
            }

            List<HealthTrendPoint> trend = monitorService.getHealthTrend(systemId, days);
            return Result.success(trend);
        } catch (Exception e) {
            log.error("获取健康度趋势失败: {}", systemId, e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取健康度分布
     */
    @GetMapping("/health/distribution")
    @Operation(summary = "获取健康度分布")
    public Result<HealthDistribution> getHealthDistribution() {
        try {
            log.info("获取健康度分布");
            HealthDistribution distribution = monitorService.getHealthDistribution();
            return Result.success(distribution);
        } catch (Exception e) {
            log.error("获取健康度分布失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取影响健康度的主要因素
     */
    @GetMapping("/systems/{systemId}/health/factors")
    @Operation(summary = "获取影响健康度的主要因素")
    public Result<List<HealthFactor>> getHealthFactors(@PathVariable String systemId) {
        try {
            log.info("获取健康因素: {}", systemId);
            List<HealthFactor> factors = monitorService.getHealthFactors(systemId);
            return Result.success(factors);
        } catch (Exception e) {
            log.error("获取健康因素失败: {}", systemId, e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    // ==================== 辅助接口 ====================

    /**
     * 刷新监控数据缓存
     */
    @PostMapping("/cache/refresh")
    @Operation(summary = "刷新监控数据缓存")
    public Result<Void> refreshCache() {
        try {
            log.info("刷新监控数据缓存");
            monitorService.refreshCache();
            return Result.success();
        } catch (Exception e) {
            log.error("刷新缓存失败", e);
            return Result.error("刷新失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public Result<String> health() {
        return Result.success("Monitor service is running");
    }
}