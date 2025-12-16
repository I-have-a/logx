package com.domidodo.logx.engine.storage.controller;

import com.domidodo.logx.common.result.Result;
import com.domidodo.logx.engine.storage.elasticsearch.BatchExportService;
import com.domidodo.logx.engine.storage.elasticsearch.ChunkedDataExporter;
import com.domidodo.logx.engine.storage.elasticsearch.EsDataExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 导出管理控制器
 * 提供索引数据导出的 API
 */
@Slf4j
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final EsDataExporter esDataExporter;
    private final ChunkedDataExporter chunkedDataExporter;
    private final BatchExportService batchExportService;

    /**
     * 导出索引数据为 JSON
     */
    @GetMapping("/index/{indexName}")
    public ResponseEntity<String> exportIndex(@PathVariable String indexName) {
        try {
            log.info("请求导出索引: {}", indexName);

            String jsonData = esDataExporter.exportIndexToJson(indexName);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=" + indexName + ".json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonData);
        } catch (Exception e) {
            log.error("导出索引失败: {}", indexName, e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 导出索引数据到流
     */
    @GetMapping("/index/{indexName}/stream")
    public ResponseEntity<byte[]> exportIndexToStream(@PathVariable String indexName) {
        try {
            log.info("请求流式导出索引: {}", indexName);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            chunkedDataExporter.exportToStream(indexName, outputStream);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=" + indexName + ".json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(outputStream.toByteArray());
        } catch (Exception e) {
            log.error("流式导出失败: {}", indexName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取索引文档数量
     */
    @GetMapping("/index/{indexName}/count")
    public Result<Long> getIndexCount(@PathVariable String indexName) {
        try {
            long count = esDataExporter.getIndexDocumentCount(indexName);
            return Result.success(count);
        } catch (Exception e) {
            log.error("获取索引文档数失败: {}", indexName, e);
            return Result.error("获取文档数失败: " + e.getMessage());
        }
    }

    /**
     * 估算导出文件大小
     */
    @GetMapping("/index/{indexName}/estimate-size")
    public Result<Map<String, Object>> estimateExportSize(@PathVariable String indexName) {
        try {
            long documentCount = esDataExporter.getIndexDocumentCount(indexName);
            long estimatedSize = chunkedDataExporter.estimateExportSize(indexName);
            boolean needsChunked = chunkedDataExporter.needsChunkedExport(indexName);

            Map<String, Object> result = Map.of(
                    "indexName", indexName,
                    "documentCount", documentCount,
                    "estimatedSize", estimatedSize,
                    "estimatedSizeMB", estimatedSize / 1024 / 1024,
                    "needsChunkedExport", needsChunked
            );

            return Result.success(result);
        } catch (Exception e) {
            log.error("估算导出大小失败: {}", indexName, e);
            return Result.error("估算失败: " + e.getMessage());
        }
    }

    /**
     * 批量导出索引
     */
    @PostMapping("/batch")
    public Result<BatchExportService.BatchExportResult> batchExport(
            @RequestBody List<BatchExportService.ExportTask> tasks) {
        try {
            log.info("请求批量导出: {} 个索引", tasks.size());

            BatchExportService.BatchExportResult result = batchExportService.batchExport(tasks);
            return Result.success(result);
        } catch (Exception e) {
            log.error("批量导出失败", e);
            return Result.error("批量导出失败: " + e.getMessage());
        }
    }

    /**
     * 获取导出进度
     */
    @GetMapping("/progress")
    public Result<Map<String, Object>> getExportProgress() {
        try {
            Map<String, Object> progress = batchExportService.getExportProgress();
            return Result.success(progress);
        } catch (Exception e) {
            log.error("获取导出进度失败", e);
            return Result.error("获取进度失败: " + e.getMessage());
        }
    }

    /**
     * 归档索引到 MinIO
     */
    @PostMapping("/archive")
    public Result<Void> archiveIndex(
            @RequestParam String indexName,
            @RequestParam String tenantId,
            @RequestParam String systemId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        try {
            log.info("请求归档索引: {} -> MinIO", indexName);

            boolean success = chunkedDataExporter.exportAndArchiveInChunks(
                    indexName, tenantId, systemId, date);

            if (success) {
                return Result.success();
            } else {
                return Result.error("归档失败");
            }
        } catch (Exception e) {
            log.error("归档索引失败: {}", indexName, e);
            return Result.error("归档失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("Export service is running");
    }
}