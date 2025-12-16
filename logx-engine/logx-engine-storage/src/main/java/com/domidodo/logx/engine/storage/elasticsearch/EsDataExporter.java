package com.domidodo.logx.engine.storage.elasticsearch;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.alibaba.fastjson2.JSON;
import com.domidodo.logx.engine.storage.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Elasticsearch 数据导出服务
 * 使用 Scroll API 分批导出大量数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsDataExporter {

    private final ElasticsearchClient elasticsearchClient;
    private final StorageConfig storageConfig;

    private static final int DEFAULT_SCROLL_SIZE = 1000;
    private static final String DEFAULT_SCROLL_TIMEOUT = "300000";

    /**
     * 导出索引全部数据为 JSON 数组字符串
     *
     * @param indexName 索引名称
     * @return JSON 数组字符串
     */
    public String exportIndexToJson(String indexName) {
        log.info("开始导出索引数据: {}", indexName);
        long startTime = System.currentTimeMillis();

        List<Map<String, Object>> allDocuments = new ArrayList<>();
        AtomicLong totalCount = new AtomicLong(0);

        try {
            // 使用 Scroll API 分批查询
            scrollQuery(indexName, document -> {
                allDocuments.add(document);
                totalCount.incrementAndGet();
            });

            String jsonResult = JSON.toJSONString(allDocuments);
            long duration = System.currentTimeMillis() - startTime;

            log.info("索引数据导出完成: {}, 文档数: {}, 耗时: {}ms, 大小: {} bytes",
                    indexName, totalCount.get(), duration, jsonResult.length());

            return jsonResult;
        } catch (Exception e) {
            log.error("导出索引数据失败: {}", indexName, e);
            throw new RuntimeException("导出索引数据失败", e);
        }
    }

    /**
     * 导出索引数据并流式处理
     * 适用于超大数据量，避免内存溢出
     *
     * @param indexName 索引名称
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
                if (batch.size() >= DEFAULT_SCROLL_SIZE) {
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
            throw new RuntimeException("流式导出失败", e);
        }
    }

    /**
     * 使用 Scroll API 查询数据
     *
     * @param indexName 索引名称
     * @param documentConsumer 文档消费者
     */
    private void scrollQuery(String indexName, Consumer<Map<String, Object>> documentConsumer)
            throws IOException {

        // 初始化 Scroll 查询
        SearchResponse<Map> response = elasticsearchClient.search(s -> s
                        .index(indexName)
                        .size(DEFAULT_SCROLL_SIZE)
                        .scroll(Time.of(t -> t.time(DEFAULT_SCROLL_TIMEOUT)))
                        .query(q -> q.matchAll(m -> m)),
                Map.class
        );

        String scrollId = response.scrollId();
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
                            .scroll(Time.of(t -> t.time(DEFAULT_SCROLL_TIMEOUT))),
                    Map.class
            );

            scrollId = scrollResponse.scrollId();
            hits = scrollResponse.hits().hits();

            if (hits != null && !hits.isEmpty()) {
                processHits(hits, documentConsumer);
            }
        }

        // 清理 Scroll 上下文
        if (StrUtil.isNotBlank(scrollId)) {
            clearScroll(scrollId);
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
            elasticsearchClient.clearScroll(c -> c.scrollId(scrollId));
            log.debug("Scroll 上下文已清理: {}", scrollId);
        } catch (Exception e) {
            log.warn("清理 Scroll 上下文失败: {}", scrollId, e);
        }
    }

    /**
     * 导出索引数据到多个 JSON 文件
     * 每个文件包含指定数量的文档
     *
     * @param indexName 索引名称
     * @param documentsPerFile 每个文件的文档数
     * @return JSON 字符串列表
     */
    public List<String> exportIndexToMultipleJsonFiles(String indexName, int documentsPerFile) {
        log.info("开始分文件导出索引数据: {}, 每文件 {} 条", indexName, documentsPerFile);

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
            throw new RuntimeException("分文件导出失败", e);
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
            CountResponse response = elasticsearchClient.count(c -> c.index(indexName));
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
     * @param indexName 索引名称
     * @param progressCallback 进度回调
     * @return JSON 字符串
     */
    public String exportIndexWithProgress(String indexName,
                                          Consumer<ExportProgress> progressCallback) {
        long totalCount = getIndexDocumentCount(indexName);
        ExportProgress progress = new ExportProgress(totalCount);

        log.info("开始导出索引数据: {}, 总文档数: {}", indexName, totalCount);

        List<Map<String, Object>> allDocuments = new ArrayList<>();

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
            throw new RuntimeException("导出失败", e);
        }
    }
}