package com.domidodo.logx.engine.processor.writer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.domidodo.logx.engine.storage.elasticsearch.EsIndexManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Elasticsearch 写入器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Import({
        com.domidodo.logx.engine.storage.elasticsearch.EsIndexManager.class,
        com.domidodo.logx.engine.storage.config.StorageConfig.class
})
public class ElasticsearchWriter {

    private final ElasticsearchClient elasticsearchClient;
    private final EsIndexManager esIndexManager;

    /**
     * 索引存在性缓存（避免频繁检查）
     * Key: 索引名称, Value: 是否存在
     */
    private final Map<String, Boolean> indexExistenceCache = new ConcurrentHashMap<>();

    /**
     * 索引前缀
     */
    private static final String INDEX_PREFIX = "logx-logs-";

    /**
     * 日期格式
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * 安全索引名称正则：只允许小写字母、数字、连字符
     */
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("^[a-z0-9-]+$");

    /**
     * 最大批量大小
     */
    @Value("${logx.es.bulk.max-size:500}")
    private int maxBulkSize;

    /**
     * 索引名称最大长度（ES限制255字符）
     */
    private static final int MAX_INDEX_NAME_LENGTH = 200;

    /**
     * 批量写入日志
     *
     * @param logs 日志列表
     * @return 成功写入的数量
     */
    public int bulkWrite(List<Map<String, Object>> logs) {
        if (logs == null || logs.isEmpty()) {
            log.warn("没有要写入的日志");
            return 0;
        }

        int totalSuccess = 0;

        try {
            // 分批处理，避免单次批量过大
            List<List<Map<String, Object>>> batches = splitIntoBatches(logs, maxBulkSize);

            for (List<Map<String, Object>> batch : batches) {
                int batchSuccess = writeBatch(batch);
                totalSuccess += batchSuccess;
            }

            log.info("批量写入已完成：总计={}, 成功={}, 失败={}",
                    logs.size(), totalSuccess, logs.size() - totalSuccess);

            return totalSuccess;

        } catch (Exception e) {
            log.error("无法将日志批量写入Elasticsearch", e);
            return totalSuccess;
        }
    }

    /**
     * 写入单个批次（添加索引自动创建）
     */
    private int writeBatch(List<Map<String, Object>> batch) {
        try {
            // 1. 预先确保所有需要的索引都存在
            Set<String> requiredIndices = new HashSet<>();
            for (Map<String, Object> log : batch) {
                String indexName = generateIndexName(log);
                requiredIndices.add(indexName);
            }

            // 2. 批量检查并创建索引
            ensureIndicesExist(requiredIndices);

            // 3. 构建批量请求
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (Map<String, Object> logOne : batch) {
                try {
                    // 生成索引名称（安全校验）
                    String indexName = generateIndexName(logOne);

                    // 使用日志ID作为文档ID，实现幂等性
                    String documentId = extractDocumentId(logOne);

                    // 添加到批量请求
                    bulkBuilder.operations(op -> op
                            .index(idx -> idx
                                    .index(indexName)
                                    .id(documentId)  // 指定ID防止重复
                                    .document(logOne)
                            )
                    );
                } catch (Exception e) {
                    log.error("向批量请求添加日志失败", e);
                }
            }

            // 4. 执行批量写入
            BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());

            // 5. 处理结果
            return processBulkResponse(response, batch.size());

        } catch (ElasticsearchException e) {
            if (e.getMessage().contains("all shards failed")) {
                log.error("所有分片失败，可能是索引不存在或配置错误", e);
                // 清除缓存，下次重新检查
                indexExistenceCache.clear();
            } else {
                log.error("Elasticsearch异常", e);
            }
            return 0;
        } catch (Exception e) {
            log.error("未能将批写入Elasticsearch", e);
            return 0;
        }
    }

    /**
     * 确保索引存在（批量检查和创建）
     */
    private void ensureIndicesExist(Set<String> indexNames) {
        for (String indexName : indexNames) {
            // 先检查缓存
            if (indexExistenceCache.getOrDefault(indexName, false)) {
                continue; // 缓存中已存在，跳过
            }

            try {
                // 检查索引是否真实存在
                boolean exists = checkIndexExists(indexName);

                if (!exists) {
                    // 索引不存在，自动创建
                    createIndexIfNeeded(indexName);
                }

                // 更新缓存
                indexExistenceCache.put(indexName, true);

            } catch (Exception e) {
                log.error("检查或创建索引失败: {}", indexName, e);
                // 不抛出异常，尝试继续处理
            }
        }
    }

    /**
     * 检查索引是否存在
     */
    private boolean checkIndexExists(String indexName) {
        try {
            ExistsRequest request = ExistsRequest.of(b -> b.index(indexName));
            return elasticsearchClient.indices().exists(request).value();
        } catch (Exception e) {
            log.warn("检查索引存在性失败: {}", indexName, e);
            return false;
        }
    }

    /**
     * 创建索引（如果需要）
     */
    private void createIndexIfNeeded(String indexName) {
        try {
            // 从索引名称解析信息
            IndexInfo indexInfo = parseIndexName(indexName);

            if (indexInfo != null) {
                log.info("自动创建索引: {}", indexName);
                esIndexManager.createLogIndex(
                        indexInfo.tenantId,
                        indexInfo.systemId,
                        indexInfo.date
                );
                log.info("索引创建成功: {}", indexName);
            } else {
                log.error("无法解析索引名称，跳过创建: {}", indexName);
            }
        } catch (Exception e) {
            log.error("创建索引失败: {}", indexName, e);
            throw new RuntimeException("创建索引失败: " + indexName, e);
        }
    }

    /**
     * 解析索引名称
     * 格式：logx-logs-{tenantId}-{systemId}-{yyyy.MM.dd}
     */
    private IndexInfo parseIndexName(String indexName) {
        try {
            // 移除前缀
            String withoutPrefix = indexName.replace(INDEX_PREFIX, "");
            String[] parts = withoutPrefix.split("-");

            if (parts.length >= 3) {
                String tenantId = parts[0];
                String systemId = parts[1];

                // 解析日期部分 (yyyy.MM.dd)
                String datePart = parts[2];
                LocalDate date = LocalDate.parse(datePart, DATE_FORMATTER);

                return new IndexInfo(tenantId, systemId, date);
            }
        } catch (Exception e) {
            log.warn("解析索引名称失败: {}", indexName, e);
        }
        return null;
    }

    /**
     * 处理批量响应
     */
    private int processBulkResponse(BulkResponse response, int batchSize) {
        int successCount = 0;
        int failCount = 0;

        if (response.errors()) {
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null) {
                    failCount++;
                    log.error("批量写入错误：index={}, id={}, 错误={}",
                            item.index(), item.id(), item.error().reason());
                } else {
                    successCount++;
                }
            }
        } else {
            successCount = batchSize;
        }

        if (failCount > 0) {
            log.warn("批写入已完成，但有错误：{}成功，{}失败",
                    successCount, failCount);
        } else {
            log.debug("批量写入成功完成：已写入{}条日志", successCount);
        }

        return successCount;
    }

    /**
     * 生成索引名称（安全版本）
     * 格式：logx-logs-{tenantId}-{systemId}-{yyyy.MM.dd}
     */
    private String generateIndexName(Map<String, Object> logOne) {
        // 获取并验证租户ID
        String tenantId = sanitizeIndexComponent(
                (String) logOne.get("tenantId"), "default");

        // 获取并验证系统ID
        String systemId = sanitizeIndexComponent(
                (String) logOne.get("systemId"), "unknown");

        // 安全的时间戳处理
        String date = extractDate(logOne);

        // 构建索引名称
        String indexName = String.format("%s%s-%s-%s",
                INDEX_PREFIX, tenantId, systemId, date);

        // 验证索引名称长度
        if (indexName.length() > MAX_INDEX_NAME_LENGTH) {
            log.warn("索引名称太长，截断：{}", indexName);
            indexName = indexName.substring(0, MAX_INDEX_NAME_LENGTH);
        }

        return indexName;
    }

    /**
     * 清理索引名称组件（防注入）
     */
    private String sanitizeIndexComponent(String input, String defaultValue) {
        if (input == null || input.isEmpty()) {
            return defaultValue;
        }

        // 转换为小写
        String sanitized = input.toLowerCase().trim();

        // 移除特殊字符，只保留字母、数字、连字符
        sanitized = sanitized.replaceAll("[^a-z0-9-]", "");

        // 限制长度（防止过长）
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }

        // 再次验证格式
        if (!SAFE_NAME_PATTERN.matcher(sanitized).matches()) {
            log.warn("索引组件无效，使用默认值：{}", input);
            return defaultValue;
        }

        return sanitized;
    }

    /**
     * 提取文档ID（实现幂等性）
     */
    private String extractDocumentId(Map<String, Object> log) {
        // 优先使用日志ID
        Object id = log.get("id");
        if (id != null) {
            return id.toString();
        }

        // 使用traceId + spanId组合
        String traceId = (String) log.get("traceId");
        String spanId = (String) log.get("spanId");
        if (traceId != null && spanId != null) {
            return traceId + "-" + spanId;
        }

        // 使用时间戳 + 随机数（最后的fallback）
        long timestamp = System.currentTimeMillis();
        int random = (int) (Math.random() * 10000);
        return timestamp + "-" + random;
    }

    /**
     * 安全的日期提取
     */
    private String extractDate(Map<String, Object> logOne) {
        Object timestamp = logOne.get("timestamp");

        try {
            LocalDateTime dateTime;

            if (timestamp instanceof LocalDateTime) {
                dateTime = (LocalDateTime) timestamp;
            } else if (timestamp instanceof Long) {
                // 毫秒时间戳转换
                dateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli((Long) timestamp),
                        ZoneId.systemDefault()
                );
            } else if (timestamp instanceof String) {
                // 尝试解析字符串时间戳
                try {
                    long millis = Long.parseLong((String) timestamp);
                    dateTime = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(millis),
                            ZoneId.systemDefault()
                    );
                } catch (NumberFormatException e) {
                    // 尝试ISO格式解析
                    dateTime = LocalDateTime.parse((String) timestamp);
                }
            } else {
                // 使用当前时间
                dateTime = LocalDateTime.now();
            }

            return dateTime.format(DATE_FORMATTER);

        } catch (Exception e) {
            log.warn("无法使用当前日期解析时间戳：{}", timestamp, e);
            return LocalDateTime.now().format(DATE_FORMATTER);
        }
    }

    /**
     * 分批处理
     */
    private List<List<Map<String, Object>>> splitIntoBatches(
            List<Map<String, Object>> logs, int batchSize) {

        List<List<Map<String, Object>>> batches = new ArrayList<>();

        for (int i = 0; i < logs.size(); i += batchSize) {
            int end = Math.min(i + batchSize, logs.size());
            batches.add(new ArrayList<>(logs.subList(i, end)));
        }

        return batches;
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
            String documentId = extractDocumentId(logOne);

            // 确保索引存在
            ensureIndicesExist(Set.of(indexName));

            elasticsearchClient.index(idx -> idx
                    .index(indexName)
                    .id(documentId)
                    .document(logOne)
            );

            log.debug("日志写入index：{}，id:{}", indexName, documentId);
            return true;

        } catch (Exception e) {
            log.error("未能将日志写入Elasticsearch", e);
            return false;
        }
    }

    /**
     * 清除索引存在性缓存
     */
    public void clearIndexCache() {
        indexExistenceCache.clear();
        log.info("索引存在性缓存已清除");
    }

    /**
     * 索引信息类
     */
    private static class IndexInfo {
        String tenantId;
        String systemId;
        LocalDate date;

        IndexInfo(String tenantId, String systemId, LocalDate date) {
            this.tenantId = tenantId;
            this.systemId = systemId;
            this.date = date;
        }
    }
}