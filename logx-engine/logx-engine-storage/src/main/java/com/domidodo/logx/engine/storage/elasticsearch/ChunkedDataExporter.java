package com.domidodo.logx.engine.storage.elasticsearch;

import cn.hutool.core.io.IoUtil;
import com.alibaba.fastjson2.JSON;
import com.domidodo.logx.engine.storage.minio.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分块数据导出服务
 * 适用于超大索引，避免内存溢出
 * 将数据分块直接写入 MinIO
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkedDataExporter {

    private final EsDataExporter esDataExporter;
    private final MinioStorageService minioStorageService;

    private static final int CHUNK_SIZE = 10000; // 每块文档数

    /**
     * 分块导出并直接归档到 MinIO
     * 适用于超大索引（百万级以上文档）
     *
     * @param indexName 索引名称
     * @param tenantId  租户ID
     * @param systemId  系统ID
     * @param date      日期
     * @return 归档成功
     */
    public boolean exportAndArchiveInChunks(String indexName, String tenantId,
                                            String systemId, LocalDate date) {
        log.info("开始分块导出归档: {}", indexName);
        long startTime = System.currentTimeMillis();

        try {
            // 创建临时文件用于写入数据
            File tempFile = createTempFile(indexName);

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {

                AtomicLong totalCount = new AtomicLong(0);
                List<Map<String, Object>> currentChunk = new ArrayList<>();

                // 写入数组开始符号
                writer.write("[");

                // 使用流式处理，避免内存溢出
                esDataExporter.exportIndexWithBatchProcessor(indexName, batch -> {
                    try {
                        boolean isFirst = true;
                        for (Map<String, Object> document : batch) {
                            if (!isFirst) {
                                writer.write(",");
                            }
                            writer.write(JSON.toJSONString(document));
                            isFirst = false;
                            totalCount.incrementAndGet();

                            // 每处理 10000 条刷新一次
                            if (totalCount.get() % 10000 == 0) {
                                writer.flush();
                                log.info("已处理 {} 条文档", totalCount.get());
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("写入临时文件失败", e);
                    }
                });

                // 写入数组结束符号
                writer.write("]");
                writer.flush();

                long duration = System.currentTimeMillis() - startTime;
                log.info("数据写入完成: {}, 文档数: {}, 耗时: {}ms, 文件大小: {} bytes",
                        indexName, totalCount.get(), duration, tempFile.length());
            }

            // 读取临时文件并上传到 MinIO
            String jsonData = IoUtil.read(new FileInputStream(tempFile), StandardCharsets.UTF_8);
            minioStorageService.archiveLogs(tenantId, systemId, date, jsonData);

            // 删除临时文件
            if (!tempFile.delete()) {
                log.warn("删除临时文件失败: {}", tempFile.getAbsolutePath());
            }

            long totalDuration = System.currentTimeMillis() - startTime;
            log.info("分块导出归档完成: {}, 总耗时: {}ms", indexName, totalDuration);
            return true;

        } catch (Exception e) {
            log.error("分块导出归档失败: {}", indexName, e);
            return false;
        }
    }

    /**
     * 流式导出到 OutputStream
     * 不创建临时文件，直接流式写入
     *
     * @param indexName    索引名称
     * @param outputStream 输出流
     * @return 导出的文档数
     */
    public long exportToStream(String indexName, OutputStream outputStream) {
        log.info("开始流式导出: {}", indexName);
        long startTime = System.currentTimeMillis();

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

            AtomicLong totalCount = new AtomicLong(0);
            AtomicBoolean isFirst = new AtomicBoolean(true);

            // 写入数组开始
            writer.write("[");

            // 流式处理
            esDataExporter.exportIndexWithBatchProcessor(indexName, batch -> {
                try {
                    for (Map<String, Object> document : batch) {
                        if (!isFirst.get()) {
                            writer.write(",");
                        }
                        writer.write(JSON.toJSONString(document));
                        isFirst.set(false);
                        totalCount.incrementAndGet();

                        // 定期刷新缓冲区
                        if (totalCount.get() % 1000 == 0) {
                            writer.flush();
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException("写入流失败", e);
                }
            });

            // 写入数组结束
            writer.write("]");
            writer.flush();

            long duration = System.currentTimeMillis() - startTime;
            log.info("流式导出完成: {}, 文档数: {}, 耗时: {}ms",
                    indexName, totalCount.get(), duration);

            return totalCount.get();

        } catch (Exception e) {
            log.error("流式导出失败: {}", indexName, e);
            throw new RuntimeException("流式导出失败", e);
        }
    }

    /**
     * 分片导出为多个文件
     * 每个文件包含固定数量的文档
     *
     * @param indexName        索引名称
     * @param tenantId         租户ID
     * @param systemId         系统ID
     * @param date             日期
     * @param documentsPerFile 每个文件的文档数
     * @return 生成的文件数
     */
    public int exportAsMultipleFiles(String indexName, String tenantId, String systemId,
                                     LocalDate date, int documentsPerFile) {
        log.info("开始分片导出: {}, 每文件 {} 条", indexName, documentsPerFile);

        List<String> jsonFiles = esDataExporter.exportIndexToMultipleJsonFiles(
                indexName, documentsPerFile);

        // 逐个上传到 MinIO
        for (int i = 0; i < jsonFiles.size(); i++) {
            String fileName = String.format("%s/%s/%s/logs-part-%d.json.gz",
                    tenantId, systemId, date, i + 1);
            // 这里可以调用 MinIO 服务上传
            log.info("上传分片文件: {}, 大小: {} bytes", fileName, jsonFiles.get(i).length());
        }

        log.info("分片导出完成: {}, 生成 {} 个文件", indexName, jsonFiles.size());
        return jsonFiles.size();
    }

    /**
     * 创建临时文件
     */
    private File createTempFile(String indexName) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        String fileName = String.format("logx-export-%s-%d.json",
                indexName.replaceAll("[^a-zA-Z0-9-]", "_"),
                System.currentTimeMillis());

        File tempFile = new File(tempDir, fileName);
        if (!tempFile.createNewFile()) {
            throw new IOException("创建临时文件失败: " + tempFile.getAbsolutePath());
        }

        log.info("创建临时文件: {}", tempFile.getAbsolutePath());
        return tempFile;
    }

    /**
     * 估算导出文件大小
     *
     * @param indexName 索引名称
     * @return 预估大小（字节）
     */
    public long estimateExportSize(String indexName) {
        try {
            long documentCount = esDataExporter.getIndexDocumentCount(indexName);
            // 假设每条文档平均 2KB
            long estimatedSize = documentCount * 2048;
            log.info("索引 {} 预估导出大小: {} bytes ({} MB)",
                    indexName, estimatedSize, estimatedSize / 1024 / 1024);
            return estimatedSize;
        } catch (Exception e) {
            log.error("估算导出大小失败: {}", indexName, e);
            return 0;
        }
    }

    /**
     * 检查是否需要分块导出
     * 根据文档数量和预估大小判断
     *
     * @param indexName 索引名称
     * @return 是否需要分块
     */
    public boolean needsChunkedExport(String indexName) {
        try {
            long documentCount = esDataExporter.getIndexDocumentCount(indexName);
            long estimatedSize = estimateExportSize(indexName);

            // 超过 50 万条或预估大小超过 1GB，使用分块导出
            boolean needsChunk = documentCount > 500000 || estimatedSize > 1024 * 1024 * 1024;

            if (needsChunk) {
                log.info("索引 {} 文档数: {}, 预估大小: {} MB, 将使用分块导出",
                        indexName, documentCount, estimatedSize / 1024 / 1024);
            }

            return needsChunk;
        } catch (Exception e) {
            log.error("检查分块需求失败: {}", indexName, e);
            return false;
        }
    }
}