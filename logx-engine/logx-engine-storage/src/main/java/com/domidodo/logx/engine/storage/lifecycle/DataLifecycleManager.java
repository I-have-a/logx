package com.domidodo.logx.engine.storage.lifecycle;

import com.domidodo.logx.engine.storage.config.StorageConfig;
import com.domidodo.logx.engine.storage.elasticsearch.ChunkedDataExporter;
import com.domidodo.logx.engine.storage.elasticsearch.EsDataExporter;
import com.domidodo.logx.engine.storage.elasticsearch.EsIndexManager;
import com.domidodo.logx.engine.storage.minio.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
     * 根据日期获取索引列表
     */
    private List<String> getIndicesByDate(LocalDate date) {
        // TODO: 实现根据日期获取索引的逻辑
        // 需要调用 Elasticsearch 的 _cat/indices API
        return new ArrayList<>();
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
     * 获取存储统计信息
     */
    public Map<String, Object> getStorageStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // ES 索引统计
            stats.put("esIndicesCount", 0); // TODO: 实现
            stats.put("esDataSize", 0L);

            // MinIO 归档统计
            stats.put("archivesCount", 0); // TODO: 实现
            stats.put("archiveSize", 0L);

            // 生命周期配置
            stats.put("hotDataDays", storageConfig.getLifecycle().getHotDataDays());
            stats.put("warmDataDays", storageConfig.getLifecycle().getWarmDataDays());
            stats.put("coldDataDays", storageConfig.getLifecycle().getColdDataDays());

        } catch (Exception e) {
            log.error("获取存储统计信息失败", e);
        }

        return stats;
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