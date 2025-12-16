package com.domidodo.logx.engine.storage.lifecycle;

import com.domidodo.logx.engine.storage.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据清理定时任务
 * 定期执行数据生命周期管理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupJob {

    private final DataLifecycleManager lifecycleManager;
    private final StorageConfig storageConfig;

    /**
     * 执行生命周期管理
     * 默认每天凌晨2点执行
     */
    @Scheduled(cron = "${logx.storage.lifecycle.cleanup-cron:0 0 2 * * ?}")
    public void executeLifecycleManagement() {
        if (!storageConfig.getLifecycle().getCleanupEnabled()) {
            log.debug("数据清理功能未启用，跳过");
            return;
        }

        log.info("=== 开始执行定时数据生命周期管理 ===");
        long startTime = System.currentTimeMillis();

        try {
            lifecycleManager.executeLifecycleManagement();

            long duration = System.currentTimeMillis() - startTime;
            log.info("=== 数据生命周期管理执行完成，耗时: {} ms ===", duration);
        } catch (Exception e) {
            log.error("=== 数据生命周期管理执行失败 ===", e);
        }
    }

    /**
     * 归档任务
     * 默认每天凌晨3点执行
     */
    @Scheduled(cron = "${logx.storage.lifecycle.archive-cron:0 0 3 * * ?}")
    public void executeArchiveTask() {
        if (!storageConfig.getLifecycle().getArchiveEnabled()) {
            log.debug("归档功能未启用，跳过");
            return;
        }

        log.info("=== 开始执行定时归档任务 ===");
        long startTime = System.currentTimeMillis();

        try {
            // 归档任务已包含在生命周期管理中，这里可以单独执行或做额外处理
            long duration = System.currentTimeMillis() - startTime;
            log.info("=== 归档任务执行完成，耗时: {} ms ===", duration);
        } catch (Exception e) {
            log.error("=== 归档任务执行失败 ===", e);
        }
    }

    /**
     * 存储统计任务
     * 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void generateStorageStats() {
        try {
            var stats = lifecycleManager.getStorageStats();
            log.info("存储统计信息: {}", stats);
        } catch (Exception e) {
            log.error("生成存储统计信息失败", e);
        }
    }
}