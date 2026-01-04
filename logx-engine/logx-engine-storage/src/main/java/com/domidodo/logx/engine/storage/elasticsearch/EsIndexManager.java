package com.domidodo.logx.engine.storage.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsRequest;
import com.domidodo.logx.engine.storage.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Elasticsearch 索引管理器
 * 负责索引的创建、删除、别名管理等
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsIndexManager {

    private final ElasticsearchTemplate elasticsearchTemplate;
    private final StorageConfig storageConfig;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private final ElasticsearchClient elasticsearchClient;

    /**
     * 创建日志索引
     *
     * @param tenantId 租户ID
     * @param systemId 系统ID
     * @param date     日期
     * @return 索引名称
     */
    public String createLogIndex(String tenantId, String systemId, LocalDate date) {
        String indexName = buildIndexName(tenantId, systemId, date);

        try {
            IndexOperations indexOps = elasticsearchTemplate.indexOps(IndexCoordinates.of(indexName));

            if (indexOps.exists()) {
                log.info("索引已存在: {}", indexName);
                return indexName;
            }

            // 创建索引设置
            Map<String, Object> settings = buildIndexSettings();

            // 创建索引映射
            Map<String, Object> mappings = buildIndexMappings();
            Document settingsDoc = Document.from(settings);
            Document mappingDoc = Document.from(mappings);

            // 创建索引
            indexOps.create(settingsDoc);
            indexOps.putMapping(mappingDoc);

            log.info("成功创建索引: {}", indexName);
            return indexName;
        } catch (Exception e) {
            log.error("创建索引失败: {}", indexName, e);
            throw new RuntimeException("创建索引失败", e);
        }
    }

    /**
     * 删除索引
     *
     * @param indexName 索引名称
     */
    public void deleteIndex(String indexName) {
        try {
            IndexOperations indexOps = elasticsearchTemplate.indexOps(IndexCoordinates.of(indexName));

            if (!indexOps.exists()) {
                log.warn("索引不存在: {}", indexName);
                return;
            }

            indexOps.delete();
            log.info("成功删除索引: {}", indexName);
        } catch (Exception e) {
            log.error("删除索引失败: {}", indexName, e);
            throw new RuntimeException("删除索引失败", e);
        }
    }

    /**
     * 删除过期索引
     *
     * @param beforeDays 保留天数
     * @return 删除的索引列表
     */
    public List<String> deleteExpiredIndices(int beforeDays) {
        List<String> deletedIndices = new ArrayList<>();
        LocalDate cutoffDate = LocalDate.now().minusDays(beforeDays);

        try {
            // 获取所有索引
            Set<String> allIndices = getAllLogIndices();

            for (String indexName : allIndices) {
                LocalDate indexDate = extractDateFromIndexName(indexName);
                if (indexDate != null && indexDate.isBefore(cutoffDate)) {
                    deleteIndex(indexName);
                    deletedIndices.add(indexName);
                }
            }

            log.info("删除了 {} 个过期索引", deletedIndices.size());
        } catch (Exception e) {
            log.error("删除过期索引失败", e);
        }

        return deletedIndices;
    }

    /**
     * 设置索引为只读
     *
     * @param indexName 索引名称
     */
    public void setIndexReadOnly(String indexName) {
        try {
            PutIndicesSettingsRequest request =
                    PutIndicesSettingsRequest.of(b -> b
                            .index(indexName)
                            .settings(s -> s
                                    .blocks(bl -> bl
                                            .write(true)
                                    )
                            )
                    );

            log.info("索引已设置为只读: {}", indexName);
        } catch (Exception e) {
            log.error("设置索引只读失败: {}", indexName, e);
            throw new RuntimeException("设置索引只读失败", e);
        }
    }


    /**
     * 获取索引统计信息
     *
     * @param indexName 索引名称
     * @return 统计信息
     */
    public Map<String, Object> getIndexStats(String indexName) {
        Map<String, Object> stats = new HashMap<>();

        try {
            IndexOperations indexOps = elasticsearchTemplate.indexOps(IndexCoordinates.of(indexName));

            if (!indexOps.exists()) {
                return stats;
            }

            // 这里可以添加更多统计信息的获取逻辑
            stats.put("indexName", indexName);
            stats.put("exists", true);

        } catch (Exception e) {
            log.error("获取索引统计信息失败: {}", indexName, e);
        }

        return stats;
    }

    /**
     * 构建索引名称
     */
    private String buildIndexName(String tenantId, String systemId, LocalDate date) {
        return String.format("%s-%s-%s-%s",
                storageConfig.getIndex().getPrefix(),
                tenantId,
                systemId,
                date.format(DATE_FORMATTER));
    }

    /**
     * 从索引名称提取日期
     */
    private LocalDate extractDateFromIndexName(String indexName) {
        try {
            String[] parts = indexName.split("-");
            if (parts.length >= 4) {
                String dateStr = parts[parts.length - 1];
                return LocalDate.parse(dateStr, DATE_FORMATTER);
            }
        } catch (Exception e) {
            log.warn("无法从索引名称提取日期: {}", indexName);
        }
        return null;
    }

    /**
     * 获取所有日志索引
     */
    private Set<String> getAllLogIndices() {
        Set<String> indices = new HashSet<>();

        try {
            // 使用 Elasticsearch 客户端获取索引列表
            GetIndexResponse response = elasticsearchClient.indices().get(b -> b
                    .index(storageConfig.getIndex().getPrefix() + "-*")
            );

            // 获取所有匹配的索引名称
            indices.addAll(response.result().keySet());

            log.info("找到 {} 个日志索引", indices.size());
        } catch (Exception e) {
            log.error("获取日志索引列表失败", e);
        }

        return indices;
    }

    /**
     * 获取所有日志索引（公共方法）
     */
    public Set<String> getAllLogIndicesPublic() {
        return getAllLogIndices();
    }

    /**
     * 构建索引设置
     */
    private Map<String, Object> buildIndexSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("number_of_shards", storageConfig.getIndex().getShards());
        settings.put("number_of_replicas", storageConfig.getIndex().getReplicas());
        settings.put("refresh_interval", storageConfig.getIndex().getRefreshInterval());

        // 压缩设置
        if (storageConfig.getCompression().getEnabled()) {
            settings.put("codec", "best_compression");
        }

        return settings;
    }

    /**
     * 构建索引映射
     */
    private Map<String, Object> buildIndexMappings() {
        Map<String, Object> mappings = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();

        // 基础字段
        properties.put("traceId", Map.of("type", "keyword"));
        properties.put("spanId", Map.of("type", "keyword"));
        properties.put("tenantId", Map.of("type", "keyword"));
        properties.put("systemId", Map.of("type", "keyword"));
        properties.put("systemName", Map.of("type", "keyword"));
        properties.put("timestamp", Map.of("type", "date"));
        properties.put("level", Map.of("type", "keyword"));
        properties.put("logger", Map.of("type", "keyword"));
        properties.put("thread", Map.of("type", "keyword"));

        // 代码位置
        properties.put("className", Map.of("type", "keyword"));
        properties.put("methodName", Map.of("type", "keyword"));
        properties.put("lineNumber", Map.of("type", "integer"));

        // 日志内容（支持中文分词）
        properties.put("message", Map.of(
                "type", "text",
                "analyzer", "ik_max_word",
                "search_analyzer", "ik_smart"
        ));

        properties.put("exception", Map.of("type", "text"));

        // 用户信息
        properties.put("userId", Map.of("type", "keyword"));
        properties.put("userName", Map.of("type", "keyword"));

        // 业务信息
        properties.put("module", Map.of("type", "keyword"));
        properties.put("operation", Map.of("type", "keyword"));

        // 请求信息
        properties.put("requestUrl", Map.of("type", "keyword"));
        properties.put("requestMethod", Map.of("type", "keyword"));
        properties.put("requestParams", Map.of("type", "text"));
        properties.put("responseTime", Map.of("type", "long"));

        // 客户端信息
        properties.put("ip", Map.of("type", "ip"));
        properties.put("userAgent", Map.of("type", "text"));

        // 标签和扩展字段
        properties.put("tags", Map.of("type", "keyword"));
        properties.put("extra", Map.of("type", "object", "enabled", false));

        mappings.put("properties", properties);
        return mappings;
    }
}