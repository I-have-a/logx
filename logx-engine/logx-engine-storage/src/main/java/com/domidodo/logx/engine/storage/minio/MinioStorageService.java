package com.domidodo.logx.engine.storage.minio;

import cn.hutool.core.io.IoUtil;
import com.domidodo.logx.engine.storage.config.StorageConfig;
import io.minio.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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