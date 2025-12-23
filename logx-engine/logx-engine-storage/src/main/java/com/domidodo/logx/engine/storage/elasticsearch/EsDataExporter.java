package com.domidodo.logx.engine.storage.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.alibaba.fastjson2.JSON;
import com.domidodo.logx.engine.storage.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Elasticsearch 数据导出服务（修复版）
 * 使用 Scroll API 分批导出大量数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsDataExporter {

    private final ElasticsearchClient elasticsearchClient;
    private final StorageConfig storageConfig;

    /**
     * 导出索引全部数据为 JSON 数组字符串
     * ⚠️ 注意：此方法会将所有数据加载到内存，仅适用于小数据量
     *
     * @param indexName 索引名称
     * @return JSON 数组字符串
     */
    public String exportIndexToJson(String indexName) {
        log.info("开始导出索引数据: {}", indexName);
        long startTime = System.currentTimeMillis();

        // 先检查数据量
        long totalCount = getIndexDocumentCount(indexName);
        if (totalCount > 100000) {
            log.warn("索引 {} 有 {} 条数据，建议使用流式导出方法", indexName, totalCount);
        }

        List<Map<String, Object>> allDocuments = new ArrayList<>();
        AtomicLong processedCount = new AtomicLong(0);

        try {
            // 使用 Scroll API 分批查询
            scrollQuery(indexName, document -> {
                allDocuments.add(document);
                processedCount.incrementAndGet();
            });

            String jsonResult = JSON.toJSONString(allDocuments);
            long duration = System.currentTimeMillis() - startTime;

            log.info("索引数据导出完成: {}, 文档数: {}, 耗时: {}ms, 大小: {} bytes",
                    indexName, processedCount.get(), duration, jsonResult.length());

            return jsonResult;
        } catch (Exception e) {
            log.error("导出索引数据失败: {}", indexName, e);
            throw new RuntimeException("导出索引数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 导出索引数据并流式处理
     * 适用于超大数据量，避免内存溢出
     *
     * @param indexName      索引名称
     * @param batchProcessor 批量处理器
     * @return 导出的文档总数
     */
    public long exportIndexWithBatchProcessor(String indexName,
                                              Consumer<List<Map<String, Object>>> batchProcessor) {
        log.info("开始流式导出索引数据: {}", indexName);
        long startTime = System.currentTimeMillis();
        AtomicLong totalCount = new AtomicLong(0);

        try {
            List<Map<String, Object>> batch = new ArrayList<>();

            scrollQuery(indexName, document -> {
                batch.add(document);
                totalCount.incrementAndGet();

                // 当批次达到大小时，调用处理器
                if (batch.size() >= storageConfig.getBulk().getSize()) {
                    batchProcessor.accept(new ArrayList<>(batch));
                    batch.clear();
                }
            });

            // 处理剩余的批次
            if (!batch.isEmpty()) {
                batchProcessor.accept(batch);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("流式导出完成: {}, 文档数: {}, 耗时: {}ms",
                    indexName, totalCount.get(), duration);

            return totalCount.get();
        } catch (Exception e) {
            log.error("流式导出失败: {}", indexName, e);
            throw new RuntimeException("流式导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 Scroll API 查询数据（修复版 - 确保资源释放）
     *
     * @param indexName        索引名称
     * @param documentConsumer 文档消费者
     */
    private void scrollQuery(String indexName, Consumer<Map<String, Object>> documentConsumer) {
        String scrollId = null;

        try {
            // 初始化 Scroll 查询
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                            .index(indexName)
                            .size(storageConfig.getBulk().getSize())
                            .scroll(Time.of(t -> t.time(storageConfig.getBulk().getFlushInterval())))
                            .query(q -> q.matchAll(m -> m)),
                    Map.class
            );

            scrollId = response.scrollId();
            List<Hit<Map>> hits = response.hits().hits();

            if (hits.isEmpty()) {
                log.info("索引 {} 没有数据", indexName);
                return;
            }

            // 处理第一批数据
            processHits(hits, documentConsumer);

            // 继续滚动查询
            while (hits != null && !hits.isEmpty()) {
                String finalScrollId = scrollId;
                ScrollResponse<Map> scrollResponse = elasticsearchClient.scroll(s -> s
                                .scrollId(finalScrollId)
                                .scroll(Time.of(t -> t.time(storageConfig.getBulk().getFlushInterval()))),
                        Map.class
                );

                scrollId = scrollResponse.scrollId();
                hits = scrollResponse.hits().hits();

                if (hits != null && !hits.isEmpty()) {
                    processHits(hits, documentConsumer);
                }
            }

        } catch (Exception e) {
            log.error("Scroll查询失败: {}", indexName, e);
            throw new RuntimeException("Scroll查询失败", e);
        } finally {
            // 确保清理 Scroll 上下文（在finally块中）
            if (scrollId != null) {
                clearScroll(scrollId);
            }
        }
    }

    /**
     * 处理查询结果
     */
    private void processHits(List<Hit<Map>> hits, Consumer<Map<String, Object>> documentConsumer) {
        for (Hit<Map> hit : hits) {
            Map<String, Object> document = hit.source();
            if (document != null) {
                // 添加元数据
                document.put("_id", hit.id());
                document.put("_index", hit.index());
                documentConsumer.accept(document);
            }
        }
    }

    /**
     * 清理 Scroll 上下文
     */
    private void clearScroll(String scrollId) {
        try {
            ClearScrollResponse response = elasticsearchClient.clearScroll(c -> c
                    .scrollId(scrollId)
            );

            if (response.succeeded()) {
                log.debug("Scroll 上下文已清理: {}", scrollId);
            } else {
                log.warn("Scroll 上下文清理失败: {}", scrollId);
            }
        } catch (Exception e) {
            log.warn("清理 Scroll 上下文失败: {}", scrollId, e);
        }
    }

    /**
     * 导出索引数据到多个 JSON 文件
     * 每个文件包含指定数量的文档
     *
     * @param indexName        索引名称
     * @param documentsPerFile 每个文件的文档数
     * @return JSON 字符串列表
     */
    public List<String> exportIndexToMultipleJsonFiles(String indexName, int documentsPerFile) {
        log.info("开始分文件导出索引数据: {}, 每文件 {} 条", indexName, documentsPerFile);

        // 添加参数验证
        if (documentsPerFile <= 0) {
            throw new IllegalArgumentException("documentsPerFile必须为正数");
        }
        if (documentsPerFile > 10000) {
            log.warn("documentsPerFile {} 太大，建议不超过10000", documentsPerFile);
        }

        List<String> jsonFiles = new ArrayList<>();
        List<Map<String, Object>> currentBatch = new ArrayList<>();

        try {
            scrollQuery(indexName, document -> {
                currentBatch.add(document);

                if (currentBatch.size() >= documentsPerFile) {
                    String jsonContent = JSON.toJSONString(currentBatch);
                    jsonFiles.add(jsonContent);
                    currentBatch.clear();
                }
            });

            // 处理最后一批
            if (!currentBatch.isEmpty()) {
                String jsonContent = JSON.toJSONString(currentBatch);
                jsonFiles.add(jsonContent);
            }

            log.info("分文件导出完成: {}, 生成文件数: {}", indexName, jsonFiles.size());
            return jsonFiles;
        } catch (Exception e) {
            log.error("分文件导出失败: {}", indexName, e);
            throw new RuntimeException("分文件导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取索引文档总数
     *
     * @param indexName 索引名称
     * @return 文档总数
     */
    public long getIndexDocumentCount(String indexName) {
        try {
            CountResponse response = elasticsearchClient.count(c -> c
                    .index(indexName)
            );
            return response.count();
        } catch (Exception e) {
            log.error("获取索引文档数失败: {}", indexName, e);
            return 0;
        }
    }

    /**
     * 导出进度信息
     */
    public static class ExportProgress {
        private final long totalDocuments;
        private final AtomicLong processedDocuments;
        private final long startTime;

        public ExportProgress(long totalDocuments) {
            this.totalDocuments = totalDocuments;
            this.processedDocuments = new AtomicLong(0);
            this.startTime = System.currentTimeMillis();
        }

        public void increment() {
            processedDocuments.incrementAndGet();
        }

        public long getProcessedCount() {
            return processedDocuments.get();
        }

        public double getProgress() {
            if (totalDocuments == 0) return 0.0;
            return (double) processedDocuments.get() / totalDocuments * 100;
        }

        public long getElapsedTime() {
            return System.currentTimeMillis() - startTime;
        }

        public long getEstimatedRemainingTime() {
            long elapsed = getElapsedTime();
            long processed = processedDocuments.get();
            if (processed == 0) return 0;

            long remaining = totalDocuments - processed;
            return (elapsed * remaining) / processed;
        }

        @Override
        public String toString() {
            return String.format("进度: %.2f%% (%d/%d), 已耗时: %dms, 预计剩余: %dms",
                    getProgress(), processedDocuments.get(), totalDocuments,
                    getElapsedTime(), getEstimatedRemainingTime());
        }
    }

    /**
     * 带进度监控的导出
     *
     * @param indexName        索引名称
     * @param progressCallback 进度回调
     * @return JSON 字符串
     */
    public String exportIndexWithProgress(String indexName,
                                          Consumer<ExportProgress> progressCallback) {
        long totalCount = getIndexDocumentCount(indexName);

        // 检查数据量
        if (totalCount > 1000000) {
            throw new IllegalArgumentException(
                    "数据量过大(" + totalCount + "条)，请使用流式导出方法");
        }

        ExportProgress progress = new ExportProgress(totalCount);

        log.info("开始导出索引数据: {}, 总文档数: {}", indexName, totalCount);

        List<Map<String, Object>> allDocuments = new ArrayList<>((int) totalCount);

        try {
            scrollQuery(indexName, document -> {
                allDocuments.add(document);
                progress.increment();

                // 每处理 1000 条记录回调一次
                if (progress.getProcessedCount() % 1000 == 0) {
                    progressCallback.accept(progress);
                }
            });

            // 最后一次回调
            progressCallback.accept(progress);

            String jsonResult = JSON.toJSONString(allDocuments);
            log.info("导出完成: {}, {}", indexName, progress);

            return jsonResult;
        } catch (Exception e) {
            log.error("导出失败: {}, {}", indexName, progress, e);
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }
    }
}