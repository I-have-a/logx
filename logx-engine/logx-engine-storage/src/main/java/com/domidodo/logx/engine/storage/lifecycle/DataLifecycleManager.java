package com.domidodo.logx.engine.storage.lifecycle;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndicesStatsResponse;
import co.elastic.clients.elasticsearch.indices.stats.IndicesStats;
import com.domidodo.logx.engine.storage.config.StorageConfig;
import com.domidodo.logx.engine.storage.elasticsearch.ChunkedDataExporter;
import com.domidodo.logx.engine.storage.elasticsearch.EsDataExporter;
import com.domidodo.logx.engine.storage.elasticsearch.EsIndexManager;
import com.domidodo.logx.engine.storage.elasticsearch.IndexPatternMatcher;
import com.domidodo.logx.engine.storage.minio.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 数据生命周期管理器
 * 负责管理日志数据在不同存储层之间的迁移
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataLifecycleManager {

    private final EsIndexManager esIndexManager;
    private final MinioStorageService minioStorageService;
    private final StorageConfig storageConfig;
    private final EsDataExporter esDataExporter;
    private final ChunkedDataExporter chunkedDataExporter;
    private final ElasticsearchClient elasticsearchClient;

    /**
     * 根据日期获取索引列表
     */
    private List<String> getIndicesByDate(LocalDate date) {
        log.info("开始获取日期 {} 之前的索引", date);
        List<String> indices = new ArrayList<>();

        try {
            Set<String> allIndices = esIndexManager.getAllLogIndicesPublic();
            IndexPatternMatcher matcher = new IndexPatternMatcher(storageConfig);

            for (String indexName : allIndices) {
                if (matcher.matchesPattern(indexName)) {
                    LocalDate indexDate = matcher.extractDate(indexName);
                    if (indexDate != null && !indexDate.isAfter(date)) {
                        indices.add(indexName);
                    }
                }
            }

            log.info("找到 {} 个符合日期条件的索引", indices.size());

            // 按日期排序（最新的在前）
            indices.sort((a, b) -> {
                LocalDate dateA = matcher.extractDate(a);
                LocalDate dateB = matcher.extractDate(b);
                if (dateA == null || dateB == null) return 0;
                return dateB.compareTo(dateA);
            });

        } catch (Exception e) {
            log.error("获取索引列表失败", e);
        }

        return indices;
    }

    /**
     * 获取索引的详细统计信息
     */
    public Map<String, Object> getIndexDetailedStats(String indexName) {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 获取索引统计
            IndicesStatsResponse response = elasticsearchClient.indices().stats(b -> b
                    .index(indexName)
            );

            IndicesStats indexStats = response.indices().get(indexName);
            if (indexStats != null) {
                // 文档统计
                stats.put("documentCount", indexStats.primaries().docs().count());
                stats.put("deletedDocumentCount", indexStats.primaries().docs().deleted());

                // 存储统计
                stats.put("storeSize", indexStats.primaries().store().size());
                stats.put("storeSizeBytes", indexStats.primaries().store().sizeInBytes());

                // 索引统计
                stats.put("indexingTime", indexStats.primaries().indexing().indexTime());
                stats.put("indexingCount", indexStats.primaries().indexing().indexTotal());

                // 查询统计
                stats.put("queryTime", indexStats.primaries().search().queryTime());
                stats.put("queryCount", indexStats.primaries().search().queryTotal());

                // 分片信息
                stats.put("shardCount", indexStats.shards().size());
            }

        } catch (Exception e) {
            log.error("获取索引 {} 的详细统计失败", indexName, e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 获取指定日期范围内的索引
     */
    public List<String> getIndicesInDateRange(LocalDate startDate, LocalDate endDate) {
        List<String> indices = new ArrayList<>();

        try {
            Set<String> allIndices = esIndexManager.getAllLogIndicesPublic();

            for (String indexName : allIndices) {
                try {
                    // 解析索引名称中的日期
                    IndexInfo indexInfo = parseIndexName(indexName);
                    if (indexInfo != null && indexInfo.date != null) {
                        // 检查日期是否在范围内
                        if (!indexInfo.date.isBefore(startDate) && !indexInfo.date.isAfter(endDate)) {
                            indices.add(indexName);
                        }
                    }
                } catch (Exception e) {
                    log.debug("无法解析索引 {} 的日期，跳过", indexName);
                }
            }

            log.info("在日期范围 {} 到 {} 中找到 {} 个索引",
                    startDate, endDate, indices.size());
        } catch (Exception e) {
            log.error("获取日期范围内的索引失败", e);
        }

        return indices;
    }

    /**
     * 获取存储统计信息
     */
    public Map<String, Object> getStorageStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 1. 获取 ES 索引统计
            Map<String, Object> esStats = getElasticsearchStats();
            stats.putAll(esStats);

            // 2. 获取 MinIO 归档统计
            Map<String, Object> archiveStats = minioStorageService.getArchiveStats();
            stats.putAll(archiveStats);

            // 3. 生命周期配置
            stats.put("hotDataDays", storageConfig.getLifecycle().getHotDataDays());
            stats.put("warmDataDays", storageConfig.getLifecycle().getWarmDataDays());
            stats.put("coldDataDays", storageConfig.getLifecycle().getColdDataDays());
            stats.put("archiveEnabled", storageConfig.getLifecycle().getArchiveEnabled());
            stats.put("cleanupEnabled", storageConfig.getLifecycle().getCleanupEnabled());

            // 4. 计算合并统计
            long esDataSize = (long) stats.getOrDefault("esDataSize", 0L);
            long archiveSize = (long) stats.getOrDefault("totalSize", 0L);
            long totalStorageSize = esDataSize + archiveSize;

            stats.put("totalStorageSize", totalStorageSize);
            stats.put("totalStorageSizeMB", totalStorageSize / (1024 * 1024));
            stats.put("totalStorageSizeGB", totalStorageSize / (1024 * 1024 * 1024.0));

            // 5. 存储类型分布
            double esPercentage = totalStorageSize > 0 ? (double) esDataSize / totalStorageSize * 100 : 0;
            double archivePercentage = totalStorageSize > 0 ? (double) archiveSize / totalStorageSize * 100 : 0;

            stats.put("esStoragePercentage", String.format("%.2f%%", esPercentage));
            stats.put("archiveStoragePercentage", String.format("%.2f%%", archivePercentage));

            // 6. 最近7天索引创建情况
            LocalDate weekAgo = LocalDate.now().minusDays(7);
            List<String> recentIndices = getIndicesByDate(LocalDate.now());
            List<String> weekOldIndices = getIndicesByDate(weekAgo);
            stats.put("indicesCreatedLast7Days", recentIndices.size() - weekOldIndices.size());

            // 7. 存储状态
            stats.put("status", "OK");
            stats.put("timestamp", LocalDateTime.now().toString());

        } catch (Exception e) {
            log.error("获取存储统计信息失败", e);
            stats.put("error", e.getMessage());
            stats.put("status", "ERROR");
        }

        return stats;
    }

    /**
     * 获取 Elasticsearch 统计信息
     */
    private Map<String, Object> getElasticsearchStats() {
        Map<String, Object> esStats = new HashMap<>();

        try {
            // 获取所有索引
            GetIndexResponse response = elasticsearchClient.indices().get(b -> b
                    .index(storageConfig.getIndex().getPrefix() + "-*")
            );

            Set<String> logIndices = response.result().keySet();
            long esIndicesCount = logIndices.size();

            // 获取索引统计
            IndicesStatsResponse statsResponse = elasticsearchClient.indices().stats(b -> b
                    .index(storageConfig.getIndex().getPrefix() + "-*")
            );

            long totalDocs = 0;
            long totalSize = 0;

            for (Map.Entry<String, IndicesStats> entry : statsResponse.indices().entrySet()) {
                IndicesStats indexStats = entry.getValue();
                totalDocs += indexStats.primaries().docs().count();
                totalSize += indexStats.primaries().store().sizeInBytes();
            }

            esStats.put("esIndicesCount", esIndicesCount);
            esStats.put("esDocumentCount", totalDocs);
            esStats.put("esDataSize", totalSize);
            esStats.put("esDataSizeMB", totalSize / (1024 * 1024));
            esStats.put("esDataSizeGB", totalSize / (1024 * 1024 * 1024.0));

        } catch (Exception e) {
            log.error("获取 Elasticsearch 统计失败", e);
            esStats.put("esError", e.getMessage());
        }

        return esStats;
    }

    /**
     * 执行完整的生命周期管理
     */
    public void executeLifecycleManagement() {
        log.info("开始执行数据生命周期管理");

        try {
            // 1. 将温数据设置为只读
            markWarmDataReadOnly();

            // 2. 归档冷数据到 MinIO
            archiveColdData();

            // 3. 删除已归档的索引
            deleteArchivedIndices();

            // 4. 删除过期的归档数据
            deleteExpiredArchives();

            log.info("数据生命周期管理执行完成");
        } catch (Exception e) {
            log.error("执行数据生命周期管理失败", e);
        }
    }

    /**
     * 标记温数据为只读
     * 温数据：7天前的数据
     */
    private void markWarmDataReadOnly() {
        log.info("开始标记温数据为只读");

        try {
            int hotDataDays = storageConfig.getLifecycle().getHotDataDays();
            LocalDate warmDataDate = LocalDate.now().minusDays(hotDataDays);

            // 获取需要标记为只读的索引
            List<String> warmIndices = getIndicesByDate(warmDataDate);

            for (String indexName : warmIndices) {
                esIndexManager.setIndexReadOnly(indexName);
                log.info("索引已标记为只读: {}", indexName);
            }

            log.info("成功标记 {} 个索引为只读", warmIndices.size());
        } catch (Exception e) {
            log.error("标记温数据为只读失败", e);
        }
    }

    /**
     * 归档冷数据到 MinIO
     * 冷数据：30天前的数据
     */
    private void archiveColdData() {
        if (!storageConfig.getLifecycle().getArchiveEnabled()) {
            log.info("归档功能未启用，跳过");
            return;
        }

        log.info("开始归档冷数据");

        try {
            int warmDataDays = storageConfig.getLifecycle().getWarmDataDays();
            LocalDate coldDataDate = LocalDate.now().minusDays(warmDataDays);

            // 获取需要归档的索引
            List<String> coldIndices = getIndicesByDate(coldDataDate);

            int archivedCount = 0;
            for (String indexName : coldIndices) {
                if (archiveIndexData(indexName)) {
                    archivedCount++;
                }
            }

            log.info("成功归档 {} 个索引", archivedCount);
        } catch (Exception e) {
            log.error("归档冷数据失败", e);
        }
    }

    /**
     * 归档单个索引的数据
     */
    private boolean archiveIndexData(String indexName) {
        try {
            log.info("开始归档索引: {}", indexName);

            // 1. 从索引名称解析租户ID、系统ID和日期
            IndexInfo indexInfo = parseIndexName(indexName);
            if (indexInfo == null) {
                log.warn("无法解析索引名称: {}", indexName);
                return false;
            }

            // 2. 检查是否已归档
            if (minioStorageService.archiveExists(
                    indexInfo.tenantId, indexInfo.systemId, indexInfo.date)) {
                log.info("索引已归档，跳过: {}", indexName);
                return false;
            }

            // 3. 判断是否需要分块导出
            boolean needsChunked = chunkedDataExporter.needsChunkedExport(indexName);

            if (needsChunked) {
                // 使用分块导出，避免内存溢出
                log.info("使用分块导出模式: {}", indexName);
                return chunkedDataExporter.exportAndArchiveInChunks(
                        indexName,
                        indexInfo.tenantId,
                        indexInfo.systemId,
                        indexInfo.date
                );
            } else {
                // 使用普通导出
                log.info("使用普通导出模式: {}", indexName);
                String jsonData = exportIndexData(indexName);
                minioStorageService.archiveLogs(
                        indexInfo.tenantId,
                        indexInfo.systemId,
                        indexInfo.date,
                        jsonData
                );
            }

            log.info("成功归档索引: {}", indexName);
            return true;
        } catch (Exception e) {
            log.error("归档索引失败: {}", indexName, e);
            return false;
        }
    }

    /**
     * 导出索引数据为 JSON
     */
    private String exportIndexData(String indexName) {
        log.info("导出索引数据: {}", indexName);

        try {
            // 获取文档总数
            long totalCount = esDataExporter.getIndexDocumentCount(indexName);
            log.info("索引 {} 包含 {} 条文档", indexName, totalCount);

            if (totalCount == 0) {
                log.warn("索引 {} 没有数据，返回空数组", indexName);
                return "[]";
            }

            // 使用带进度监控的导出
            return esDataExporter.exportIndexWithProgress(indexName, progress -> {
                if (progress.getProcessedCount() % 5000 == 0) {
                    log.info("导出进度: {}", progress);
                }
            });
        } catch (Exception e) {
            log.error("导出索引数据失败: {}", indexName, e);
            throw new RuntimeException("导出索引数据失败", e);
        }
    }

    /**
     * 删除已归档的索引
     */
    private void deleteArchivedIndices() {
        log.info("开始删除已归档的索引");

        try {
            int warmDataDays = storageConfig.getLifecycle().getWarmDataDays();
            LocalDate archiveDate = LocalDate.now().minusDays(warmDataDays);

            // 获取需要删除的索引
            List<String> indicesToDelete = getIndicesByDate(archiveDate);

            int deletedCount = 0;
            for (String indexName : indicesToDelete) {
                IndexInfo indexInfo = parseIndexName(indexName);
                if (indexInfo != null &&
                    minioStorageService.archiveExists(
                            indexInfo.tenantId, indexInfo.systemId, indexInfo.date)) {
                    esIndexManager.deleteIndex(indexName);
                    deletedCount++;
                }
            }

            log.info("成功删除 {} 个已归档的索引", deletedCount);
        } catch (Exception e) {
            log.error("删除已归档的索引失败", e);
        }
    }

    /**
     * 删除过期的归档数据
     */
    private void deleteExpiredArchives() {
        log.info("开始删除过期的归档数据");

        try {
            int coldDataDays = storageConfig.getLifecycle().getColdDataDays();
            List<String> deletedArchives = minioStorageService.deleteExpiredArchives(coldDataDays);

            log.info("成功删除 {} 个过期归档", deletedArchives.size());
        } catch (Exception e) {
            log.error("删除过期归档失败", e);
        }
    }

    /**
     * 解析索引名称
     * 格式：logx-logs-tenantId-systemId-yyyy.MM.dd
     */
    private IndexInfo parseIndexName(String indexName) {
        try {
            String[] parts = indexName.split("-");
            if (parts.length >= 5) {
                String tenantId = parts[2];
                String systemId = parts[3];
                String dateStr = parts[4] + "-" + parts[5] + "-" + parts[6];
                LocalDate date = LocalDate.parse(dateStr.replace(".", "-"));

                return new IndexInfo(tenantId, systemId, date);
            }
        } catch (Exception e) {
            log.warn("解析索引名称失败: {}", indexName, e);
        }
        return null;
    }

    /**
     * 索引信息
     */
    private static class IndexInfo {
        String tenantId;
        String systemId;
        LocalDate date;

        public IndexInfo(String tenantId, String systemId, LocalDate date) {
            this.tenantId = tenantId;
            this.systemId = systemId;
            this.date = date;
        }
    }
}