package com.domidodo.logx.console.api.controller;

import com.domidodo.logx.common.dto.LogDTO;
import com.domidodo.logx.common.dto.QueryDTO;
import com.domidodo.logx.common.result.PageResult;
import com.domidodo.logx.common.result.Result;
import com.domidodo.logx.console.api.service.LogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 日志查询控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Tag(name = "日志查询", description = "日志查询相关接口")
public class LogQueryController {

    private final LogQueryService logQueryService;

    /**
     * 分页查询日志
     */
    @PostMapping("/query")
    @Operation(summary = "分页查询日志")
    public Result<PageResult<LogDTO>> queryLogs(@RequestBody QueryDTO queryDTO) {
        try {
            PageResult<LogDTO> result = logQueryService.queryLogs(queryDTO);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Query logs failed", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据 TraceId 查询日志链路
     */
    @GetMapping("/trace/{traceId}")
    @Operation(summary = "根据TraceId查询日志链路")
    public Result<List<LogDTO>> queryByTraceId(@PathVariable String traceId) {
        try {
            List<LogDTO> logs = logQueryService.queryByTraceId(traceId);
            return Result.success(logs);
        } catch (Exception e) {
            log.error("Query logs by traceId failed: {}", traceId, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 导出日志
     */
    @PostMapping("/export")
    @Operation(summary = "导出日志")
    public Result<String> exportLogs(@RequestBody QueryDTO queryDTO) {
        // TODO: 实现日志导出功能
        return Result.error("功能开发中");
    }
}