package com.domidodo.logx.engine.processor.writer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
     * 批量写入日志（修复版）
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

            log.info("批量写入已完成：总计={}，成功={}、失败={}",
                    logs.size(), totalSuccess, logs.size() - totalSuccess);

            return totalSuccess;

        } catch (Exception e) {
            log.error("无法将日志批量写入Elasticsearch", e);
            return totalSuccess;
        }
    }

    /**
     * 写入单个批次
     */
    private int writeBatch(List<Map<String, Object>> batch) {
        try {
            // 构建批量请求
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

            // 执行批量写入
            BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());

            // 处理结果
            int successCount = 0;
            int failCount = 0;

            if (response.errors()) {
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        failCount++;
                        log.error("批量写入错误：index＝{}，id＝{}、错误＝{}",
                                item.index(), item.id(), item.error().reason());
                    } else {
                        successCount++;
                    }
                }
            } else {
                successCount = batch.size();
            }

            if (failCount > 0) {
                log.warn("批写入已完成，但有错误：{}成功，{}失败",
                        successCount, failCount);
            } else {
                log.debug("批量写入成功完成：已写入{}条日志", successCount);
            }

            return successCount;

        } catch (Exception e) {
            log.error("未能将批写入Elasticsearch", e);
            return 0;
        }
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
}