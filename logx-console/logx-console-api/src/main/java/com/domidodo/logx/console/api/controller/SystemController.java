package com.domidodo.logx.console.api.controller;

import com.domidodo.logx.common.dto.SystemDTO;
import com.domidodo.logx.common.result.PageResult;
import com.domidodo.logx.common.result.Result;
import com.domidodo.logx.console.api.service.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 系统管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/systems")
@RequiredArgsConstructor
@Tag(name = "系统管理", description = "系统管理相关接口")
public class SystemController {

    private final SystemService systemService;

    /**
     * 分页查询系统列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询系统列表")
    public Result<PageResult<SystemDTO>> listSystems(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        try {
            PageResult<SystemDTO> result = systemService.listSystems(page, size);
            return Result.success(result);
        } catch (Exception e) {
            log.error("List systems failed", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询系统详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询系统详情")
    public Result<SystemDTO> getSystem(@PathVariable Long id) {
        try {
            SystemDTO system = systemService.getSystemById(id);
            return Result.success(system);
        } catch (Exception e) {
            log.error("Get system failed: id={}", id, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据系统ID查询
     */
    @GetMapping("/systemId/{systemId}")
    @Operation(summary = "根据系统ID查询")
    public Result<SystemDTO> getSystemBySystemId(@PathVariable String systemId) {
        try {
            SystemDTO system = systemService.getSystemBySystemId(systemId);
            return Result.success(system);
        } catch (Exception e) {
            log.error("Get system by systemId failed: {}", systemId, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 创建系统
     */
    @PostMapping
    @Operation(summary = "创建系统")
    public Result<SystemDTO> createSystem(@RequestBody SystemDTO dto) {
        try {
            SystemDTO result = systemService.createSystem(dto);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Create system failed", e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新系统
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新系统")
    public Result<SystemDTO> updateSystem(@PathVariable Long id, @RequestBody SystemDTO dto) {
        try {
            SystemDTO result = systemService.updateSystem(id, dto);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Update system failed: id={}", id, e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除系统
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除系统")
    public Result<Void> deleteSystem(@PathVariable Long id) {
        try {
            systemService.deleteSystem(id);
            return Result.success();
        } catch (Exception e) {
            log.error("Delete system failed: id={}", id, e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 重置API Key
     */
    @PostMapping("/{id}/reset-api-key")
    @Operation(summary = "重置API Key")
    public Result<String> resetApiKey(@PathVariable Long id) {
        try {
            String newApiKey = systemService.resetApiKey(id);
            return Result.success(newApiKey);
        } catch (Exception e) {
            log.error("Reset API key failed: id={}", id, e);
            return Result.error("重置失败: " + e.getMessage());
        }
    }

    /**
     * 验证API Key
     */
    @PostMapping("/validate")
    @Operation(summary = "验证API Key")
    public Result<Boolean> validateApiKey(
            @RequestParam String apiKey,
            @RequestParam String tenantId,
            @RequestParam String systemId) {
        try {
            boolean valid = systemService.validateApiKey(apiKey, tenantId, systemId);
            return Result.success(valid);
        } catch (Exception e) {
            log.error("Validate API key failed", e);
            return Result.error("验证失败: " + e.getMessage());
        }
    }
}