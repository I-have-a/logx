package com.domidodo.logx.engine.processor.writer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 写入器
 * 批量写入日志到 ES
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchWriter {

    private final ElasticsearchClient elasticsearchClient;

    /**
     * 索引前缀
     */
    private static final String INDEX_PREFIX = "logx-logs-";

    /**
     * 日期格式
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * 批量写入日志
     *
     * @param logs 日志列表
     * @return 成功写入的数量
     */
    public int bulkWrite(List<Map<String, Object>> logs) {
        if (logs == null || logs.isEmpty()) {
            log.warn("No logs to write");
            return 0;
        }

        try {
            // 构建批量请求
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (Map<String, Object> logOne : logs) {
                try {
                    // 生成索引名称（按租户、系统、日期分隔）
                    String indexName = generateIndexName(logOne);

                    // 添加到批量请求
                    bulkBuilder.operations(op -> op
                            .index(idx -> idx
                                    .index(indexName)
                                    .document(logOne)
                            )
                    );
                } catch (Exception e) {
                    log.error("Failed to add log to bulk request: {}", logOne, e);
                }
            }

            // 执行批量写入
            BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());

            // 处理结果
            int successCount = 0;
            int failCount = 0;

            if (response.errors()) {
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        failCount++;
                        log.error("Bulk write error: index={}, error={}",
                                item.index(), item.error().reason());
                    } else {
                        successCount++;
                    }
                }
            } else {
                successCount = logs.size();
            }

            if (failCount > 0) {
                log.warn("Bulk write completed with errors: {} success, {} failed", successCount, failCount);
            } else {
                log.debug("Bulk write completed successfully: {} logs written", successCount);
            }

            return successCount;

        } catch (Exception e) {
            log.error("Failed to bulk write logs to Elasticsearch", e);
            return 0;
        }
    }

    /**
     * 生成索引名称
     * 格式：logx-logs-{tenantId}-{systemId}-{yyyy.MM.dd}
     */
    private String generateIndexName(Map<String, Object> log) {
        String tenantId = (String) log.get("tenantId");
        String systemId = (String) log.get("systemId");

        // 获取日期
        Object timestamp = log.get("timestamp");
        String date;
        if (timestamp instanceof LocalDateTime) {
            date = ((LocalDateTime) timestamp).format(DATE_FORMATTER);
        } else {
            date = LocalDateTime.now().format(DATE_FORMATTER);
        }

        // 构建索引名称
        StringBuilder indexName = new StringBuilder(INDEX_PREFIX);

        if (tenantId != null && !tenantId.isEmpty()) {
            indexName.append(tenantId.toLowerCase()).append("-");
        }

        if (systemId != null && !systemId.isEmpty()) {
            indexName.append(systemId.toLowerCase()).append("-");
        }

        indexName.append(date);

        return indexName.toString();
    }

    /**
     * 单条写入（用于测试或特殊场景）
     *
     * @param logOne 日志数据
     * @return 是否成功
     */
    public boolean write(Map<String, Object> logOne) {
        try {
            String indexName = generateIndexName(logOne);

            elasticsearchClient.index(idx -> idx
                    .index(indexName)
                    .document(logOne)
            );

            log.debug("Log written to index: {}", indexName);
            return true;

        } catch (Exception e) {
            log.error("Failed to write log to Elasticsearch: {}", logOne, e);
            return false;
        }
    }
}