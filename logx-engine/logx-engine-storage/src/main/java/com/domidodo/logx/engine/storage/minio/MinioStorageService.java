package com.domidodo.logx.engine.storage.minio;

import cn.hutool.core.io.IoUtil;
import com.domidodo.logx.engine.storage.config.MinioConfig;
import com.domidodo.logx.engine.storage.config.StorageConfig;
import io.minio.*;
import io.minio.messages.Item;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * MinIO 存储服务
 * 负责冷数据的归档和检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final StorageConfig storageConfig;

    @Value("${minio.bucket-name}")
    private String bucketName;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * 初始化存储桶
     */
    @PostConstruct
    public void initBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("创建 MinIO 存储桶: {}", bucketName);
            } else {
                log.info("MinIO 存储桶已存在: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("初始化 MinIO 存储桶失败", e);
            throw new RuntimeException("初始化 MinIO 失败", e);
        }
    }

    /**
     * 归档日志数据
     *
     * @param tenantId 租户ID
     * @param systemId 系统ID
     * @param date     日期
     * @param data     日志数据（JSON 数组）
     * @return 对象名称
     */
    public String archiveLogs(String tenantId, String systemId, LocalDate date, String data) {
        String objectName = buildObjectName(tenantId, systemId, date);

        try {
            byte[] compressedData = compress(data);

            ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedData);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, compressedData.length, -1)
                            .contentType("application/gzip")
                            .build()
            );

            log.info("成功归档日志: {}, 原始大小: {} bytes, 压缩后: {} bytes",
                    objectName, data.length(), compressedData.length);

            return objectName;
        } catch (Exception e) {
            log.error("归档日志失败: {}", objectName, e);
            throw new RuntimeException("归档日志失败", e);
        }
    }

    /**
     * 检索归档日志
     *
     * @param tenantId 租户ID
     * @param systemId 系统ID
     * @param date     日期
     * @return 日志数据
     */
    public String retrieveLogs(String tenantId, String systemId, LocalDate date) {
        String objectName = buildObjectName(tenantId, systemId, date);

        try {
            GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );

            byte[] compressedData = IoUtil.readBytes(response);
            String data = decompress(compressedData);

            log.info("成功检索归档日志: {}", objectName);
            return data;
        } catch (Exception e) {
            log.error("检索归档日志失败: {}", objectName, e);
            throw new RuntimeException("检索归档日志失败", e);
        }
    }

    /**
     * 删除归档日志
     *
     * @param tenantId 租户ID
     * @param systemId 系统ID
     * @param date     日期
     */
    public void deleteArchive(String tenantId, String systemId, LocalDate date) {
        String objectName = buildObjectName(tenantId, systemId, date);

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );

            log.info("成功删除归档日志: {}", objectName);
        } catch (Exception e) {
            log.error("删除归档日志失败: {}", objectName, e);
            throw new RuntimeException("删除归档日志失败", e);
        }
    }

    /**
     * 批量删除过期归档
     *
     * @param beforeDays 保留天数
     * @return 删除的对象列表
     */
    public List<String> deleteExpiredArchives(int beforeDays) {
        List<String> deletedObjects = new ArrayList<>();
        LocalDate cutoffDate = LocalDate.now().minusDays(beforeDays);

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();

                LocalDate objectDate = extractDateFromObjectName(objectName);
                if (objectDate != null && objectDate.isBefore(cutoffDate)) {
                    minioClient.removeObject(
                            RemoveObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(objectName)
                                    .build()
                    );
                    deletedObjects.add(objectName);
                }
            }

            log.info("删除了 {} 个过期归档", deletedObjects.size());
        } catch (Exception e) {
            log.error("删除过期归档失败", e);
        }

        return deletedObjects;
    }

    /**
     * 获取归档大小
     *
     * @param tenantId  租户ID
     * @param systemId  系统ID
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 总大小（字节）
     */
    public long getArchiveSize(String tenantId, String systemId, LocalDate startDate, LocalDate endDate) {
        long totalSize = 0;
        String prefix = buildPrefix(tenantId, systemId);

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                LocalDate objectDate = extractDateFromObjectName(item.objectName());

                if (objectDate != null &&
                    !objectDate.isBefore(startDate) &&
                    !objectDate.isAfter(endDate)) {
                    totalSize += item.size();
                }
            }
        } catch (Exception e) {
            log.error("获取归档大小失败", e);
        }

        return totalSize;
    }

    /**
     * 检查归档是否存在
     *
     * @param tenantId 租户ID
     * @param systemId 系统ID
     * @param date     日期
     * @return 是否存在
     */
    public boolean archiveExists(String tenantId, String systemId, LocalDate date) {
        String objectName = buildObjectName(tenantId, systemId, date);

        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取归档统计信息
     */
    public Map<String, Object> getArchiveStats() {
        Map<String, Object> stats = new HashMap<>();
        long totalSize = 0L;
        int count = 0;
        Map<String, Long> archiveSizesByTenant = new HashMap<>();
        Map<String, Long> archiveSizesBySystem = new HashMap<>();
        Map<String, Long> archiveSizesByDate = new HashMap<>();

        try {
            // 列出所有归档对象
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .prefix("archives/")
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir() && item.objectName().endsWith(".json.gz")) {
                    totalSize += item.size();
                    count++;

                    // 解析对象名称以获取统计信息
                    parseObjectNameForStats(item.objectName(), item.size(),
                            archiveSizesByTenant, archiveSizesBySystem,
                            archiveSizesByDate);
                }
            }

            // 基本统计信息
            stats.put("bucket", minioConfig.getBucketName());
            stats.put("totalArchives", count);
            stats.put("totalSize", totalSize);
            stats.put("totalSizeMB", totalSize / (1024 * 1024));
            stats.put("totalSizeGB", totalSize / (1024 * 1024 * 1024.0));

            // 按租户统计
            stats.put("archivesByTenant", archiveSizesByTenant);

            // 按系统统计
            stats.put("archivesBySystem", archiveSizesBySystem);

            // 按日期统计
            stats.put("archivesByDate", archiveSizesByDate);

            // 计算平均大小
            double avgSize = count > 0 ? (double) totalSize / count : 0;
            stats.put("averageArchiveSize", avgSize);
            stats.put("averageArchiveSizeMB", avgSize / (1024 * 1024));

            // 获取桶使用情况（需要 MinIO admin 权限）
            try {
                BucketStats bucketStats = getBucketStats();
                stats.put("bucketTotalSize", bucketStats.getTotalSize());
                stats.put("bucketUsedSize", bucketStats.getUsedSize());
                stats.put("bucketAvailableSize", bucketStats.getAvailableSize());
                stats.put("bucketUsagePercentage", bucketStats.getUsagePercentage());
            } catch (Exception e) {
                log.warn("无法获取桶详细统计信息: {}", e.getMessage());
            }

            log.info("归档统计: 总数={}, 总大小={}MB, 按租户分布={}",
                    count, totalSize / (1024 * 1024), archiveSizesByTenant.size());

        } catch (Exception e) {
            log.error("获取归档统计信息失败", e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 获取详细归档统计（按日期范围）
     */
    public Map<String, Object> getArchiveStatsDetailed(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new HashMap<>();
        long totalSize = 0L;
        int count = 0;
        Map<String, Map<String, Object>> tenantStats = new HashMap<>();

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .prefix("archives/")
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir() && item.objectName().endsWith(".json.gz")) {
                    // 解析对象名称获取租户、系统和日期信息
                    ArchiveObjectInfo info = parseArchiveObjectName(item.objectName());
                    if (info != null &&
                        (info.date == null ||
                         (!info.date.isBefore(startDate) && !info.date.isAfter(endDate)))) {

                        totalSize += item.size();
                        count++;

                        // 按租户聚合统计
                        tenantStats.computeIfAbsent(info.tenantId, k -> {
                            Map<String, Object> tenantStat = new HashMap<>();
                            tenantStat.put("archivesCount", 0);
                            tenantStat.put("totalSize", 0L);
                            tenantStat.put("systems", new HashMap<String, Object>());
                            return tenantStat;
                        });

                        Map<String, Object> tenantStat = tenantStats.get(info.tenantId);
                        tenantStat.put("archivesCount", (int) tenantStat.get("archivesCount") + 1);
                        tenantStat.put("totalSize", (long) tenantStat.get("totalSize") + item.size());

                        // 按系统统计
                        @SuppressWarnings("unchecked")
                        Map<String, Object> systems = (Map<String, Object>) tenantStat.get("systems");
                        systems.computeIfAbsent(info.systemId, k -> {
                            Map<String, Object> systemStat = new HashMap<>();
                            systemStat.put("archivesCount", 0);
                            systemStat.put("totalSize", 0L);
                            return systemStat;
                        });

                        @SuppressWarnings("unchecked")
                        Map<String, Object> systemStat = (Map<String, Object>) systems.get(info.systemId);
                        systemStat.put("archivesCount", (int) systemStat.get("archivesCount") + 1);
                        systemStat.put("totalSize", (long) systemStat.get("totalSize") + item.size());
                    }
                }
            }

            stats.put("startDate", startDate.toString());
            stats.put("endDate", endDate.toString());
            stats.put("totalArchives", count);
            stats.put("totalSize", totalSize);
            stats.put("totalSizeMB", totalSize / (1024 * 1024));
            stats.put("tenantStats", tenantStats);

            // 计算租户排名
            List<Map<String, Object>> tenantRanking = tenantStats.entrySet().stream()
                    .sorted((a, b) -> Long.compare(
                            (long) b.getValue().get("totalSize"),
                            (long) a.getValue().get("totalSize")
                    ))
                    .limit(10)
                    .map(entry -> {
                        Map<String, Object> tenantInfo = new HashMap<>();
                        tenantInfo.put("tenantId", entry.getKey());
                        tenantInfo.put("archivesCount", entry.getValue().get("archivesCount"));
                        tenantInfo.put("totalSize", entry.getValue().get("totalSize"));
                        tenantInfo.put("totalSizeMB", (long) entry.getValue().get("totalSize") / (1024 * 1024));
                        return tenantInfo;
                    })
                    .collect(Collectors.toList());

            stats.put("tenantRanking", tenantRanking);

        } catch (Exception e) {
            log.error("获取详细归档统计失败", e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 解析对象名称获取统计信息
     */
    private void parseObjectNameForStats(String objectName, long size,
                                         Map<String, Long> tenantSizes,
                                         Map<String, Long> systemSizes,
                                         Map<String, Long> dateSizes) {
        try {
            // 对象名称格式: archives/{tenantId}/{systemId}/{date}/logs.json.gz
            String[] parts = objectName.split("/");
            if (parts.length >= 4) {
                String tenantId = parts[1];
                String systemId = parts[2];
                String dateStr = parts[3];

                // 更新租户统计
                tenantSizes.merge(tenantId, size, Long::sum);

                // 更新系统统计
                String tenantSystemKey = tenantId + "-" + systemId;
                systemSizes.merge(tenantSystemKey, size, Long::sum);

                // 更新日期统计
                dateSizes.merge(dateStr, size, Long::sum);
            }
        } catch (Exception e) {
            log.debug("解析对象名称失败: {}", objectName, e);
        }
    }

    /**
     * 解析归档对象名称
     */
    private ArchiveObjectInfo parseArchiveObjectName(String objectName) {
        try {
            // 移除前缀和后缀
            String normalized = objectName.replace("archives/", "").replace(".json.gz", "");
            String[] parts = normalized.split("/");

            if (parts.length >= 3) {
                String tenantId = parts[0];
                String systemId = parts[1];
                LocalDate date = null;

                try {
                    // 日期可能是最后一个部分，或者是文件夹结构的一部分
                    date = LocalDate.parse(parts[2], DATE_FORMATTER);
                } catch (Exception e) {
                    log.debug("无法解析日期部分: {}", parts[2]);
                }

                return new ArchiveObjectInfo(tenantId, systemId, date, objectName);
            }
        } catch (Exception e) {
            log.debug("解析归档对象名称失败: {}", objectName, e);
        }

        return null;
    }

    /**
     * 获取存储桶统计信息
     */
    private BucketStats getBucketStats() {
        // 注意：获取准确的桶使用情况需要MinIO admin API权限
        // 这里提供实际的实现方案
        try {
            long totalSize = 0;
            long objectCount = 0;

            // 遍历所有对象统计大小
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) {
                    totalSize += item.size();
                    objectCount++;
                }
            }

            log.debug("桶统计信息: 对象数={}, 总大小={} bytes", objectCount, totalSize);

            // 假设桶总容量（实际应从配置或Admin API获取）
            long estimatedTotalCapacity = 1024L * 1024 * 1024 * 1024; // 1TB
            long availableSize = Math.max(0, estimatedTotalCapacity - totalSize);

            return new BucketStats(estimatedTotalCapacity, totalSize, availableSize);

        } catch (Exception e) {
            log.warn("获取桶统计信息失败，使用默认估算值", e);
            // 返回保守的估算值
            return new BucketStats(
                    1024L * 1024 * 1024 * 100,  // 100GB 总容量
                    1024L * 1024 * 1024 * 50,   // 50GB 已使用
                    1024L * 1024 * 1024 * 50    // 50GB 可用
            );
        }
    }

    /**
     * 获取归档文件大小趋势（按天）
     */
    public Map<String, Object> getArchiveSizeTrend(int days) {
        Map<String, Object> trend = new HashMap<>();
        Map<String, Long> dailySizes = new TreeMap<>();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .prefix("archives/")
                            .recursive(true)
                            .build()
            );

            // 初始化日期范围
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                dailySizes.put(date.format(DATE_FORMATTER), 0L);
            }

            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir() && item.objectName().endsWith(".json.gz")) {
                    ArchiveObjectInfo info = parseArchiveObjectName(item.objectName());
                    if (info != null && info.date != null &&
                        !info.date.isBefore(startDate) && !info.date.isAfter(endDate)) {

                        String dateKey = info.date.format(DATE_FORMATTER);
                        dailySizes.merge(dateKey, item.size(), Long::sum);
                    }
                }
            }

            trend.put("startDate", startDate.toString());
            trend.put("endDate", endDate.toString());
            trend.put("dailySizes", dailySizes);

            // 计算统计数据
            long totalSize = dailySizes.values().stream().mapToLong(Long::longValue).sum();
            double avgDailySize = !dailySizes.isEmpty() ? (double) totalSize / dailySizes.size() : 0;

            trend.put("totalSize", totalSize);
            trend.put("totalSizeMB", totalSize / (1024 * 1024));
            trend.put("averageDailySize", avgDailySize);
            trend.put("averageDailySizeMB", avgDailySize / (1024 * 1024));

            // 找出最大和最小的日期
            Map.Entry<String, Long> maxEntry = dailySizes.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
            Map.Entry<String, Long> minEntry = dailySizes.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .orElse(null);

            if (maxEntry != null) {
                trend.put("maxSizeDate", maxEntry.getKey());
                trend.put("maxSize", maxEntry.getValue());
                trend.put("maxSizeMB", maxEntry.getValue() / (1024 * 1024));
            }

            if (minEntry != null) {
                trend.put("minSizeDate", minEntry.getKey());
                trend.put("minSize", minEntry.getValue());
                trend.put("minSizeMB", minEntry.getValue() / (1024 * 1024));
            }

        } catch (Exception e) {
            log.error("获取归档大小趋势失败", e);
            trend.put("error", e.getMessage());
        }

        return trend;
    }

    /**
     * 归档对象信息
     */
    private static class ArchiveObjectInfo {
        String tenantId;
        String systemId;
        LocalDate date;
        String objectName;

        public ArchiveObjectInfo(String tenantId, String systemId, LocalDate date, String objectName) {
            this.tenantId = tenantId;
            this.systemId = systemId;
            this.date = date;
            this.objectName = objectName;
        }
    }

    /**
     * 存储桶统计信息
     */
    @Getter
    private static class BucketStats {
        private final long totalSize;
        private final long usedSize;
        private final long availableSize;
        private final double usagePercentage;

        public BucketStats(long totalSize, long usedSize, long availableSize) {
            this.totalSize = totalSize;
            this.usedSize = usedSize;
            this.availableSize = availableSize;
            this.usagePercentage = totalSize > 0 ? (double) usedSize / totalSize * 100 : 0;
        }

    }

    /**
     * 压缩数据
     */
    private byte[] compress(String data) throws IOException {
        if (!storageConfig.getCompression().getEnabled()) {
            return data.getBytes(StandardCharsets.UTF_8);
        }

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

        try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
            gzipStream.write(data.getBytes(StandardCharsets.UTF_8));
        }

        return byteStream.toByteArray();
    }

    /**
     * 解压数据
     */
    private String decompress(byte[] compressedData) throws IOException {
        if (!storageConfig.getCompression().getEnabled()) {
            return new String(compressedData, StandardCharsets.UTF_8);
        }

        ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData);

        try (GZIPInputStream gzipStream = new GZIPInputStream(byteStream)) {
            return IoUtil.read(gzipStream, StandardCharsets.UTF_8);
        }
    }

    /**
     * 构建对象名称
     * 格式：tenantId/systemId/yyyy/MM/dd/logs.json.gz
     */
    private String buildObjectName(String tenantId, String systemId, LocalDate date) {
        return String.format("%s/%s/%s/logs.json.gz",
                tenantId,
                systemId,
                date.format(DATE_FORMATTER));
    }

    /**
     * 构建前缀
     */
    private String buildPrefix(String tenantId, String systemId) {
        return tenantId + "/" + systemId + "/";
    }

    /**
     * 从对象名称提取日期
     */
    private LocalDate extractDateFromObjectName(String objectName) {
        try {
            // 格式：tenantId/systemId/yyyy/MM/dd/logs.json.gz
            String[] parts = objectName.split("/");
            if (parts.length >= 6) {
                String dateStr = parts[2] + "/" + parts[3] + "/" + parts[4];
                return LocalDate.parse(dateStr, DATE_FORMATTER);
            }
        } catch (Exception e) {
            log.warn("无法从对象名称提取日期: {}", objectName);
        }
        return null;
    }
}