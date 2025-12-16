package com.domidodo.logx.engine.storage.elasticsearch;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量并发导出服务
 * 支持多个索引的并发导出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchExportService {

    private final ChunkedDataExporter chunkedDataExporter;
    private ThreadPoolTaskExecutor executor;

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 5;
    private static final int QUEUE_CAPACITY = 100;

    @PostConstruct
    public void init() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("批量导出线程池已初始化: core={}, max={}", CORE_POOL_SIZE, MAX_POOL_SIZE);
    }

    @PreDestroy
    public void destroy() {
        if (executor != null) {
            executor.shutdown();
            log.info("批量导出线程池已关闭");
        }
    }

    /**
     * 批量导出多个索引
     *
     * @param exportTasks 导出任务列表
     * @return 批量导出结果
     */
    public BatchExportResult batchExport(List<ExportTask> exportTasks) {
        log.info("开始批量导出: {} 个索引", exportTasks.size());
        long startTime = System.currentTimeMillis();

        BatchExportResult result = new BatchExportResult();
        result.setTotalTasks(exportTasks.size());
        result.setStartTime(LocalDateTime.now());

        List<CompletableFuture<ExportTaskResult>> futures = new ArrayList<>();

        // 提交所有任务
        for (ExportTask task : exportTasks) {
            CompletableFuture<ExportTaskResult> future = CompletableFuture.supplyAsync(() -> {
                return executeExportTask(task);
            }, executor);
            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        try {
            allFutures.get(30, TimeUnit.MINUTES); // 最多等待30分钟
        } catch (Exception e) {
            log.error("批量导出等待超时", e);
        }

        // 收集结果
        List<ExportTaskResult> taskResults = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (CompletableFuture<ExportTaskResult> future : futures) {
            try {
                ExportTaskResult taskResult = future.get();
                taskResults.add(taskResult);
                if (taskResult.isSuccess()) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                }
            } catch (Exception e) {
                log.error("获取导出结果失败", e);
                failureCount.incrementAndGet();
            }
        }

        result.setSuccessCount(successCount.get());
        result.setFailureCount(failureCount.get());
        result.setTaskResults(taskResults);
        result.setEndTime(LocalDateTime.now());
        result.setDuration(System.currentTimeMillis() - startTime);

        log.info("批量导出完成: 总数={}, 成功={}, 失败={}, 耗时={}ms",
                result.getTotalTasks(), result.getSuccessCount(),
                result.getFailureCount(), result.getDuration());

        return result;
    }

    /**
     * 执行单个导出任务
     */
    private ExportTaskResult executeExportTask(ExportTask task) {
        ExportTaskResult result = new ExportTaskResult();
        result.setIndexName(task.getIndexName());
        result.setStartTime(LocalDateTime.now());

        try {
            log.info("开始导出任务: {}", task.getIndexName());

            boolean success = chunkedDataExporter.exportAndArchiveInChunks(
                    task.getIndexName(),
                    task.getTenantId(),
                    task.getSystemId(),
                    task.getDate()
            );

            result.setSuccess(success);
            result.setEndTime(LocalDateTime.now());

            if (success) {
                log.info("导出任务成功: {}", task.getIndexName());
            } else {
                log.warn("导出任务失败: {}", task.getIndexName());
                result.setErrorMessage("导出失败");
            }

        } catch (Exception e) {
            log.error("导出任务异常: {}", task.getIndexName(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }

        return result;
    }

    /**
     * 导出任务
     */
    @Data
    public static class ExportTask {
        private String indexName;
        private String tenantId;
        private String systemId;
        private java.time.LocalDate date;
    }

    /**
     * 导出任务结果
     */
    @Data
    public static class ExportTaskResult {
        private String indexName;
        private boolean success;
        private String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;

        public long getDuration() {
            if (startTime == null || endTime == null) {
                return 0;
            }
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    /**
     * 批量导出结果
     */
    @Data
    public static class BatchExportResult {
        private int totalTasks;
        private int successCount;
        private int failureCount;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long duration;
        private List<ExportTaskResult> taskResults;

        public double getSuccessRate() {
            if (totalTasks == 0) return 0.0;
            return (double) successCount / totalTasks * 100;
        }
    }

    /**
     * 获取导出进度
     *
     * @return 当前活跃任务数
     */
    public Map<String, Object> getExportProgress() {
        return Map.of(
                "activeCount", executor.getActiveCount(),
                "poolSize", executor.getPoolSize(),
                "queueSize", executor.getThreadPoolExecutor().getQueue().size(),
                "completedTaskCount", executor.getThreadPoolExecutor().getCompletedTaskCount()
        );
    }
}