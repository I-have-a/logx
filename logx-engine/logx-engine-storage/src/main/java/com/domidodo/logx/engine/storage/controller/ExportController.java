package com.domidodo.logx.engine.storage.controller;

import com.domidodo.logx.common.result.Result;
import com.domidodo.logx.engine.storage.elasticsearch.BatchExportService;
import com.domidodo.logx.engine.storage.elasticsearch.ChunkedDataExporter;
import com.domidodo.logx.engine.storage.elasticsearch.EsDataExporter;
import com.domidodo.logx.engine.storage.excel.ExcelExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 导出管理控制器
 * 提供索引数据导出的 API（支持 JSON 和 Excel 格式）
 */
@Slf4j
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@Tag(name = "数据导出", description = "日志数据导出管理接口")
public class ExportController {

    private final EsDataExporter esDataExporter;
    private final ChunkedDataExporter chunkedDataExporter;
    private final BatchExportService batchExportService;
    private final ExcelExportService excelExportService;

    // ==================== JSON 导出 ====================

    /**
     * 导出索引数据为 JSON
     */
    @GetMapping("/index/{indexName}")
    @Operation(summary = "导出索引为JSON", description = "将指定索引的全部数据导出为JSON格式")
    public ResponseEntity<String> exportIndex(
            @Parameter(description = "索引名称") @PathVariable String indexName) {
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
    @Operation(summary = "流式导出索引为JSON", description = "适用于大数据量的流式导出")
    public ResponseEntity<byte[]> exportIndexToStream(
            @Parameter(description = "索引名称") @PathVariable String indexName) {
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

    // ==================== Excel 导出 ====================

    /**
     * 导出索引数据为 Excel
     */
    @GetMapping("/index/{indexName}/excel")
    @Operation(summary = "导出索引为Excel", description = "将指定索引的全部数据导出为Excel格式")
    public void exportIndexToExcel(
            @Parameter(description = "索引名称") @PathVariable String indexName,
            HttpServletResponse response) {
        try {
            log.info("请求导出索引为 Excel: {}", indexName);

            // 检查数据量
            long docCount = esDataExporter.getIndexDocumentCount(indexName);
            if (docCount == 0) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"error\": \"索引不存在或无数据\"}");
                return;
            }

            // 设置响应头
            String fileName = URLEncoder.encode(indexName + ".xlsx", StandardCharsets.UTF_8);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

            // 导出 Excel
            long exportedCount = excelExportService.exportToExcel(indexName, response.getOutputStream());
            log.info("Excel 导出完成: {}, 记录数: {}", indexName, exportedCount);

        } catch (Exception e) {
            log.error("Excel 导出失败: {}", indexName, e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
            } catch (Exception ex) {
                log.error("写入错误响应失败", ex);
            }
        }
    }

    /**
     * 导出索引数据为多 Sheet Excel（适用于超大数据量）
     */
    @GetMapping("/index/{indexName}/excel/multi-sheet")
    @Operation(summary = "多Sheet导出Excel", description = "超大数据量时自动分Sheet导出，每Sheet最多100万行")
    public void exportIndexToExcelMultiSheet(
            @Parameter(description = "索引名称") @PathVariable String indexName,
            HttpServletResponse response) {
        try {
            log.info("请求多 Sheet 导出 Excel: {}", indexName);

            String fileName = URLEncoder.encode(indexName + ".xlsx", StandardCharsets.UTF_8);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);

            long exportedCount = excelExportService.exportToExcelMultiSheet(indexName, response.getOutputStream());
            log.info("多 Sheet Excel 导出完成: {}, 记录数: {}", indexName, exportedCount);

        } catch (Exception e) {
            log.error("多 Sheet Excel 导出失败: {}", indexName, e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (Exception ex) {
                log.error("设置错误状态失败", ex);
            }
        }
    }

    /**
     * 按日期范围导出 Excel
     * 每天的数据放在单独的 Sheet
     */
    @GetMapping("/excel/date-range")
    @Operation(summary = "按日期范围导出Excel", description = "导出指定日期范围内的日志，每天一个Sheet")
    public void exportByDateRange(
            @Parameter(description = "租户ID") @RequestParam String tenantId,
            @Parameter(description = "系统ID") @RequestParam String systemId,
            @Parameter(description = "开始日期 (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期 (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            HttpServletResponse response) {
        try {
            log.info("请求按日期范围导出 Excel: tenant={}, system={}, {} ~ {}",
                    tenantId, systemId, startDate, endDate);

            // 文件名：租户_系统_开始日期_结束日期.xlsx
            String fileName = String.format("%s_%s_%s_%s.xlsx",
                    tenantId, systemId,
                    startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                    endDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

            long exportedCount = excelExportService.exportByDateRange(
                    tenantId, systemId, startDate, endDate, response.getOutputStream());
            log.info("日期范围 Excel 导出完成: {} 条", exportedCount);

        } catch (Exception e) {
            log.error("日期范围 Excel 导出失败", e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (Exception ex) {
                log.error("设置错误状态失败", ex);
            }
        }
    }

    /**
     * 预览 Excel 导出（返回估算信息）
     */
    @GetMapping("/index/{indexName}/excel/preview")
    @Operation(summary = "预览Excel导出", description = "返回预估的导出文件大小和记录数")
    public Result<Map<String, Object>> previewExcelExport(
            @Parameter(description = "索引名称") @PathVariable String indexName) {
        try {
            long documentCount = esDataExporter.getIndexDocumentCount(indexName);
            long estimatedSize = excelExportService.estimateExcelSize(indexName);
            boolean needsMultiSheet = excelExportService.needsMultiFileExport(indexName);

            Map<String, Object> result = Map.of(
                    "indexName", indexName,
                    "documentCount", documentCount,
                    "estimatedSizeBytes", estimatedSize,
                    "estimatedSizeMB", String.format("%.2f", estimatedSize / 1024.0 / 1024.0),
                    "needsMultiSheet", needsMultiSheet,
                    "recommendedApi", needsMultiSheet ?
                            "/api/export/index/" + indexName + "/excel/multi-sheet" :
                            "/api/export/index/" + indexName + "/excel"
            );

            return Result.success(result);
        } catch (Exception e) {
            log.error("预览 Excel 导出失败: {}", indexName, e);
            return Result.error("预览失败: " + e.getMessage());
        }
    }

    // ==================== 通用接口 ====================

    /**
     * 获取索引文档数量
     */
    @GetMapping("/index/{indexName}/count")
    @Operation(summary = "获取索引文档数", description = "返回指定索引的文档总数")
    public Result<Long> getIndexCount(
            @Parameter(description = "索引名称") @PathVariable String indexName) {
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
    @Operation(summary = "估算导出文件大小", description = "返回预估的JSON导出文件大小")
    public Result<Map<String, Object>> estimateExportSize(
            @Parameter(description = "索引名称") @PathVariable String indexName) {
        try {
            long documentCount = esDataExporter.getIndexDocumentCount(indexName);
            long estimatedJsonSize = chunkedDataExporter.estimateExportSize(indexName);
            long estimatedExcelSize = excelExportService.estimateExcelSize(indexName);
            boolean needsChunked = chunkedDataExporter.needsChunkedExport(indexName);

            Map<String, Object> result = Map.of(
                    "indexName", indexName,
                    "documentCount", documentCount,
                    "json", Map.of(
                            "estimatedSizeBytes", estimatedJsonSize,
                            "estimatedSizeMB", String.format("%.2f", estimatedJsonSize / 1024.0 / 1024.0),
                            "needsChunkedExport", needsChunked
                    ),
                    "excel", Map.of(
                            "estimatedSizeBytes", estimatedExcelSize,
                            "estimatedSizeMB", String.format("%.2f", estimatedExcelSize / 1024.0 / 1024.0),
                            "needsMultiSheet", excelExportService.needsMultiFileExport(indexName)
                    )
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
    @Operation(summary = "批量导出索引", description = "并发导出多个索引到MinIO归档")
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
    @Operation(summary = "获取导出进度", description = "返回当前批量导出任务的进度")
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
    @Operation(summary = "归档索引到MinIO", description = "将索引数据归档到对象存储")
    public Result<Void> archiveIndex(
            @Parameter(description = "索引名称") @RequestParam String indexName,
            @Parameter(description = "租户ID") @RequestParam String tenantId,
            @Parameter(description = "系统ID") @RequestParam String systemId,
            @Parameter(description = "日期")
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
    @Operation(summary = "健康检查", description = "检查导出服务状态")
    public Result<String> health() {
        return Result.success("Export service is running");
    }
}