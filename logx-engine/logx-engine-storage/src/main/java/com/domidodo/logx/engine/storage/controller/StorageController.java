package com.domidodo.logx.engine.storage.controller;

import com.domidodo.logx.common.result.Result;
import com.domidodo.logx.engine.storage.elasticsearch.EsIndexManager;
import com.domidodo.logx.engine.storage.lifecycle.DataLifecycleManager;
import com.domidodo.logx.engine.storage.lifecycle.HotColdStrategy;
import com.domidodo.logx.engine.storage.minio.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 存储管理控制器
 * 提供存储管理和监控的 API
 */
@Slf4j
@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final EsIndexManager esIndexManager;
    private final MinioStorageService minioStorageService;
    private final DataLifecycleManager lifecycleManager;
    private final HotColdStrategy hotColdStrategy;

    /**
     * 获取存储统计信息
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStorageStats() {
        try {
            Map<String, Object> stats = lifecycleManager.getStorageStats();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取存储统计信息失败", e);
            return Result.error("获取存储统计信息失败");
        }
    }

    /**
     * 获取存储策略配置
     */
    @GetMapping("/strategy")
    public Result<Map<String, Object>> getStrategyConfig() {
        try {
            Map<String, Object> config = hotColdStrategy.getStrategyConfig();
            return Result.success(config);
        } catch (Exception e) {
            log.error("获取存储策略配置失败", e);
            return Result.error("获取存储策略配置失败");
        }
    }

    /**
     * 创建日志索引
     */
    @PostMapping("/index/create")
    public Result<String> createIndex(
            @RequestParam String tenantId,
            @RequestParam String systemId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        try {
            String indexName = esIndexManager.createLogIndex(tenantId, systemId, date);
            return Result.success(indexName);
        } catch (Exception e) {
            log.error("创建索引失败", e);
            return Result.error("创建索引失败: " + e.getMessage());
        }
    }

    /**
     * 删除索引
     */
    @DeleteMapping("/index/delete")
    public Result<Void> deleteIndex(@RequestParam String indexName) {
        try {
            esIndexManager.deleteIndex(indexName);
            return Result.success();
        } catch (Exception e) {
            log.error("删除索引失败", e);
            return Result.error("删除索引失败: " + e.getMessage());
        }
    }

    /**
     * 获取索引统计信息
     */
    @GetMapping("/index/stats")
    public Result<Map<String, Object>> getIndexStats(@RequestParam String indexName) {
        try {
            Map<String, Object> stats = esIndexManager.getIndexStats(indexName);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取索引统计信息失败", e);
            return Result.error("获取索引统计信息失败");
        }
    }

    /**
     * 手动执行生命周期管理
     */
    @PostMapping("/lifecycle/execute")
    public Result<Void> executeLifecycleManagement() {
        try {
            lifecycleManager.executeLifecycleManagement();
            return Result.success();
        } catch (Exception e) {
            log.error("执行生命周期管理失败", e);
            return Result.error("执行生命周期管理失败: " + e.getMessage());
        }
    }

    /**
     * 检查归档是否存在
     */
    @GetMapping("/archive/exists")
    public Result<Boolean> archiveExists(
            @RequestParam String tenantId,
            @RequestParam String systemId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        try {
            boolean exists = minioStorageService.archiveExists(tenantId, systemId, date);
            return Result.success(exists);
        } catch (Exception e) {
            log.error("检查归档失败", e);
            return Result.error("检查归档失败: " + e.getMessage());
        }
    }

    /**
     * 检索归档日志
     */
    @GetMapping("/archive/retrieve")
    public Result<String> retrieveArchive(
            @RequestParam String tenantId,
            @RequestParam String systemId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        try {
            String data = minioStorageService.retrieveLogs(tenantId, systemId, date);
            return Result.success(data);
        } catch (Exception e) {
            log.error("检索归档日志失败", e);
            return Result.error("检索归档日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取归档大小
     */
    @GetMapping("/archive/size")
    public Result<Long> getArchiveSize(
            @RequestParam String tenantId,
            @RequestParam String systemId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        try {
            long size = minioStorageService.getArchiveSize(tenantId, systemId, startDate, endDate);
            return Result.success(size);
        } catch (Exception e) {
            log.error("获取归档大小失败", e);
            return Result.error("获取归档大小失败: " + e.getMessage());
        }
    }

    /**
     * 判断数据层级
     */
    @GetMapping("/tier/determine")
    public Result<String> determineDataTier(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        try {
            HotColdStrategy.DataTier tier = hotColdStrategy.determineDataTier(date);
            String description = hotColdStrategy.getDataTierDescription(tier);
            return Result.success(tier.name() + ": " + description);
        } catch (Exception e) {
            log.error("判断数据层级失败", e);
            return Result.error("判断数据层级失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("Storage service is running");
    }
}