package com.domidodo.logx.engine.storage;

import com.domidodo.logx.engine.storage.config.StorageConfig;
import com.domidodo.logx.engine.storage.elasticsearch.BatchExportService;
import com.domidodo.logx.engine.storage.elasticsearch.ChunkedDataExporter;
import com.domidodo.logx.engine.storage.elasticsearch.EsDataExporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Elasticsearch 数据导出测试
 */
@SpringBootTest
class EsDataExporterTest {

    @Autowired
    private EsDataExporter esDataExporter;

    @Autowired
    private ChunkedDataExporter chunkedDataExporter;

    @Autowired
    private BatchExportService batchExportService;

    /**
     * 测试基础导出功能
     */
    @Test
    void testExportIndexToJson() {
        String indexName = "logx-logs-company_a-erp_system-2024.12.16";

        // 获取文档数量
        long count = esDataExporter.getIndexDocumentCount(indexName);
        System.out.println("索引文档数: " + count);

        if (count > 0 && count < 10000) {
            // 导出为 JSON
            String jsonData = esDataExporter.exportIndexToJson(indexName);

            assertNotNull(jsonData);
            assertTrue(jsonData.startsWith("["));
            assertTrue(jsonData.endsWith("]"));

            System.out.println("导出成功，数据大小: " + jsonData.length() + " bytes");
        } else {
            System.out.println("跳过测试：索引为空或文档数过多");
        }
    }

    /**
     * 测试带进度监控的导出
     */
    @Test
    void testExportWithProgress() {
        String indexName = "logx-logs-company_a-erp_system-2024.12.16";

        long count = esDataExporter.getIndexDocumentCount(indexName);
        if (count == 0) {
            System.out.println("索引为空，跳过测试");
            return;
        }

        String jsonData = esDataExporter.exportIndexWithProgress(indexName, progress -> {
            System.out.println("导出进度: " + progress);
        });

        assertNotNull(jsonData);
        System.out.println("导出完成");
    }

    /**
     * 测试流式处理
     */
    @Test
    void testExportWithBatchProcessor() {
        String indexName = "logx-logs-company_a-erp_system-2024.12.16";

        AtomicLong totalProcessed = new AtomicLong(0);
        AtomicLong batchCount = new AtomicLong(0);

        long count = esDataExporter.exportIndexWithBatchProcessor(indexName, batch -> {
            batchCount.incrementAndGet();
            totalProcessed.addAndGet(batch.size());
            System.out.println("处理第 " + batchCount.get() + " 批，" +
                               "包含 " + batch.size() + " 条文档");
        });

        System.out.println("总共处理: " + totalProcessed.get() + " 条文档");
        assertEquals(count, totalProcessed.get());
    }

    /**
     * 测试估算导出大小
     */
    @Test
    void testEstimateExportSize() {
        String indexName = "logx-logs-company_a-erp_system-2024.12.16";

        long estimatedSize = chunkedDataExporter.estimateExportSize(indexName);
        boolean needsChunked = chunkedDataExporter.needsChunkedExport(indexName);

        System.out.println("预估大小: " + estimatedSize + " bytes");
        System.out.println("预估大小: " + (estimatedSize / 1024 / 1024) + " MB");
        System.out.println("需要分块导出: " + needsChunked);

        assertTrue(estimatedSize >= 0);
    }

    /**
     * 测试分块导出
     */
    @Test
    void testChunkedExport() {
        String indexName = "logx-logs-company_a-erp_system-2024.11.01";
        String tenantId = "company_a";
        String systemId = "erp_system";
        LocalDate date = LocalDate.of(2024, 11, 1);

        // 检查是否需要分块
        if (chunkedDataExporter.needsChunkedExport(indexName)) {
            System.out.println("索引较大，使用分块导出");

            boolean success = chunkedDataExporter.exportAndArchiveInChunks(
                    indexName, tenantId, systemId, date);

            assertTrue(success, "分块导出应该成功");
            System.out.println("分块导出完成");
        } else {
            System.out.println("索引较小，不需要分块导出");
        }
    }

    /**
     * 测试批量导出
     */
    @Test
    void testBatchExport() {
        // 创建测试任务
        List<BatchExportService.ExportTask> tasks = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            BatchExportService.ExportTask task = new BatchExportService.ExportTask();
            task.setIndexName("logx-logs-company_a-erp_system-2024.11." + String.format("%02d", i));
            task.setTenantId("company_a");
            task.setSystemId("erp_system");
            task.setDate(LocalDate.of(2024, 11, i));
            tasks.add(task);
        }

        System.out.println("开始批量导出 " + tasks.size() + " 个索引");

        BatchExportService.BatchExportResult result = batchExportService.batchExport(tasks);

        System.out.println("批量导出完成:");
        System.out.println("  总任务数: " + result.getTotalTasks());
        System.out.println("  成功: " + result.getSuccessCount());
        System.out.println("  失败: " + result.getFailureCount());
        System.out.println("  成功率: " + result.getSuccessRate() + "%");
        System.out.println("  总耗时: " + result.getDuration() + "ms");

        // 打印每个任务的详情
        result.getTaskResults().forEach(taskResult -> {
            System.out.println("  - " + taskResult.getIndexName() + ": " +
                               (taskResult.isSuccess() ? "成功" : "失败") +
                               ", 耗时: " + taskResult.getDuration() + "ms");
        });

        assertEquals(tasks.size(), result.getTotalTasks());
    }

    /**
     * 测试导出进度查询
     */
    @Test
    void testGetExportProgress() {
        var progress = batchExportService.getExportProgress();

        System.out.println("导出进度:");
        System.out.println("  活跃任务: " + progress.get("activeCount"));
        System.out.println("  线程池大小: " + progress.get("poolSize"));
        System.out.println("  队列大小: " + progress.get("queueSize"));
        System.out.println("  已完成任务: " + progress.get("completedTaskCount"));

        assertNotNull(progress);
    }

    /**
     * 性能测试：导出速度
     */
    @Test
    void testExportPerformance() {
        String indexName = "logx-logs-company_a-erp_system-2024.12.16";

        long count = esDataExporter.getIndexDocumentCount(indexName);
        if (count == 0 || count > 100000) {
            System.out.println("跳过性能测试");
            return;
        }

        System.out.println("开始性能测试，文档数: " + count);

        // 测试普通导出
        long start1 = System.currentTimeMillis();
        String jsonData1 = esDataExporter.exportIndexToJson(indexName);
        long time1 = System.currentTimeMillis() - start1;

        System.out.println("普通导出:");
        System.out.println("  耗时: " + time1 + "ms");
        System.out.println("  速度: " + (count * 1000 / time1) + " 条/秒");
        System.out.println("  数据大小: " + (jsonData1.length() / 1024) + " KB");

        // 测试流式导出
        long start2 = System.currentTimeMillis();
        AtomicLong processed = new AtomicLong(0);
        esDataExporter.exportIndexWithBatchProcessor(indexName, batch -> {
            processed.addAndGet(batch.size());
        });
        long time2 = System.currentTimeMillis() - start2;

        System.out.println("流式导出:");
        System.out.println("  耗时: " + time2 + "ms");
        System.out.println("  速度: " + (processed.get() * 1000 / time2) + " 条/秒");

        // 对比
        System.out.println("性能对比:");
        if (time1 < time2) {
            System.out.println("  普通导出快 " + ((time2 - time1) * 100 / time2) + "%");
        } else {
            System.out.println("  流式导出快 " + ((time1 - time2) * 100 / time1) + "%");
        }
    }
}