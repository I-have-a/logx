package com.domidodo.gateway.http.controller;


import com.domidodo.gateway.http.service.LogIngestService;
import com.domidodo.logx.common.dto.LogDTO;
import com.domidodo.logx.common.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "日志接收", description = "日志接收接口")
public class LogIngestController {

    private final LogIngestService ingestService;

    /**
     * 接收单条日志
     */
    @PostMapping("/log")
    public Result<Void> ingestLog(@Valid @RequestBody LogDTO logDTO) {
        log.debug("接收日志: {}", logDTO.getMessage());
        ingestService.ingest(logDTO);
        return Result.success();
    }

    /**
     * 批量接收日志
     */
    @PostMapping("/logs")
    public Result<Map<String, Object>> ingestLogs(@RequestBody List<@Valid LogDTO> logs) {
        log.debug("批量接收日志: {} 条", logs.size());
        return ingestService.ingestBatch(logs);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("OK");
    }
}
