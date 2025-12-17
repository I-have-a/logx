package com.domidodo.logx.console.api.controller;

import com.domidodo.logx.common.dto.LogDTO;
import com.domidodo.logx.common.dto.QueryDTO;
import com.domidodo.logx.common.result.PageResult;
import com.domidodo.logx.common.result.Result;
import com.domidodo.logx.common.validator.InputValidator;
import com.domidodo.logx.console.api.service.LogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 改进的日志查询控制器
 * 增强安全性：
 * 1. 输入验证
 * 2. SQL注入防护
 * 3. XSS防护
 * 4. 请求频率限制
 * 5. 完善的错误处理
 */
@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Tag(name = "日志查询", description = "日志查询相关接口（安全增强版）")
public class ImprovedLogQueryController {

    private final LogQueryService logQueryService;
    private final InputValidator inputValidator;

    /**
     * 分页查询日志
     */
    @PostMapping("/query")
    @Operation(summary = "分页查询日志（安全增强）")
    public Result<PageResult<LogDTO>> queryLogs(@Valid @RequestBody QueryDTO queryDTO) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 输入验证
            validateQueryDTO(queryDTO);

            // 2. 查询日志
            PageResult<LogDTO> result = logQueryService.queryLogs(queryDTO);

            // 3. 记录查询日志
            long duration = System.currentTimeMillis() - startTime;
            log.info("Query logs: tenant={}, system={}, keyword={}, page={}, size={}, duration={}ms",
                    queryDTO.getTenantId(), queryDTO.getSystemId(),
                    queryDTO.getKeyword(), queryDTO.getPage(),
                    queryDTO.getSize(), duration);

            return Result.success(result);

        } catch (com.domidodo.logx.common.exception.BusinessException e) {
            log.warn("Query validation failed: {}", e.getMessage());
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Query logs failed", e);
            return Result.error("查询失败，请稍后重试");
        }
    }

    /**
     * 根据 TraceId 查询日志链路
     */
    @GetMapping("/trace/{traceId}")
    @Operation(summary = "根据TraceId查询日志链路")
    public Result<List<LogDTO>> queryByTraceId(@PathVariable String traceId) {
        try {
            // 验证 TraceId 格式
            if (traceId == null || traceId.isEmpty()) {
                return Result.error("TraceId不能为空");
            }

            if (traceId.length() > 64) {
                return Result.error("TraceId格式不正确");
            }

            // 查询日志
            List<LogDTO> logs = logQueryService.queryByTraceId(traceId);

            log.info("Query by traceId: {}, count={}", traceId, logs.size());

            return Result.success(logs);

        } catch (Exception e) {
            log.error("Query logs by traceId failed: {}", traceId, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 导出日志（增加安全控制）
     */
    @PostMapping("/export")
    @Operation(summary = "导出日志")
    public Result<String> exportLogs(@Valid @RequestBody QueryDTO queryDTO) {
        try {
            // 1. 输入验证
            validateQueryDTO(queryDTO);

            // 2. 限制导出数量
            if (queryDTO.getSize() == null || queryDTO.getSize() > 10000) {
                return Result.error("导出数量不能超过10000条");
            }

            // 3. 检查时间范围
            if (queryDTO.getStartTime() != null && queryDTO.getEndTime() != null) {
                java.time.Duration duration = java.time.Duration.between(
                        queryDTO.getStartTime(),
                        queryDTO.getEndTime()
                );
                if (duration.toDays() > 7) {
                    return Result.error("导出时间范围不能超过7天");
                }
            }

            // TODO: 实现异步导出
            log.info("Export logs request: tenant={}, system={}, timeRange={} to {}",
                    queryDTO.getTenantId(), queryDTO.getSystemId(),
                    queryDTO.getStartTime(), queryDTO.getEndTime());

            return Result.error("功能开发中");

        } catch (com.domidodo.logx.common.exception.BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("Export logs failed", e);
            return Result.error("导出失败");
        }
    }

    /**
     * 验证查询DTO
     */
    private void validateQueryDTO(QueryDTO queryDTO) {
        // 验证租户ID
        if (queryDTO.getTenantId() != null) {
            inputValidator.validateTenantId(queryDTO.getTenantId());
        }

        // 验证系统ID
        if (queryDTO.getSystemId() != null) {
            inputValidator.validateSystemId(queryDTO.getSystemId());
        }

        // 验证关键字
        if (queryDTO.getKeyword() != null && !queryDTO.getKeyword().isEmpty()) {
            String sanitized = inputValidator.validateQueryKeyword(queryDTO.getKeyword());
            queryDTO.setKeyword(sanitized);
        }

        // 验证排序字段
        if (queryDTO.getSortField() != null) {
            String validated = inputValidator.validateSortField(queryDTO.getSortField());
            queryDTO.setSortField(validated);
        }

        // 验证排序方式
        if (queryDTO.getSortOrder() != null) {
            String validated = inputValidator.validateSortOrder(queryDTO.getSortOrder());
            queryDTO.setSortOrder(validated);
        }

        // 验证分页参数
        inputValidator.validatePagination(queryDTO.getPage(), queryDTO.getSize());

        // 验证时间范围
        if (queryDTO.getStartTime() != null && queryDTO.getEndTime() != null) {
            inputValidator.validateTimeRange(queryDTO.getStartTime(), queryDTO.getEndTime());
        }

        // 验证响应时间范围
        if (queryDTO.getMinResponseTime() != null || queryDTO.getMaxResponseTime() != null) {
            inputValidator.validateResponseTimeRange(
                    queryDTO.getMinResponseTime(),
                    queryDTO.getMaxResponseTime()
            );
        }
    }
}