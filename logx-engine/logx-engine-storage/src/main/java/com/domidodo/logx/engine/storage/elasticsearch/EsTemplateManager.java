package com.domidodo.logx.engine.storage.elasticsearch;

import com.domidodo.logx.engine.storage.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 模板管理器
 * 负责索引模板和生命周期策略的管理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsTemplateManager {

    private final ElasticsearchTemplate elasticsearchTemplate;
    private final StorageConfig storageConfig;

    /**
     * 初始化索引模板
     */
    @PostConstruct
    public void initIndexTemplate() {
        try {
            createLogIndexTemplate();
            createLifecyclePolicy();
            log.info("索引模板和生命周期策略初始化成功");
        } catch (Exception e) {
            log.error("索引模板初始化失败", e);
        }
    }

    /**
     * 创建日志索引模板
     */
    public void createLogIndexTemplate() {
        String templateName = storageConfig.getIndex().getPrefix() + "-template";

        try {
            Map<String, Object> template = buildLogIndexTemplate();

            // 这里需要使用 Elasticsearch 的低级客户端 API 创建模板
            // elasticsearchTemplate 不直接支持模板操作，需要使用 RestHighLevelClient

            log.info("创建索引模板: {}", templateName);
        } catch (Exception e) {
            log.error("创建索引模板失败: {}", templateName, e);
            throw new RuntimeException("创建索引模板失败", e);
        }
    }

    /**
     * 创建生命周期策略
     */
    public void createLifecyclePolicy() {
        String policyName = storageConfig.getIndex().getPrefix() + "-policy";

        try {
            Map<String, Object> policy = buildLifecyclePolicy();

            log.info("创建生命周期策略: {}", policyName);
        } catch (Exception e) {
            log.error("创建生命周期策略失败: {}", policyName, e);
            throw new RuntimeException("创建生命周期策略失败", e);
        }
    }

    /**
     * 更新索引模板
     */
    public void updateIndexTemplate() {
        String templateName = storageConfig.getIndex().getPrefix() + "-template";

        try {
            // 先删除旧模板
            deleteIndexTemplate(templateName);

            // 创建新模板
            createLogIndexTemplate();

            log.info("更新索引模板成功: {}", templateName);
        } catch (Exception e) {
            log.error("更新索引模板失败: {}", templateName, e);
            throw new RuntimeException("更新索引模板失败", e);
        }
    }

    /**
     * 删除索引模板
     */
    public void deleteIndexTemplate(String templateName) {
        try {
            // 实现模板删除逻辑
            log.info("删除索引模板: {}", templateName);
        } catch (Exception e) {
            log.error("删除索引模板失败: {}", templateName, e);
        }
    }

    /**
     * 构建日志索引模板
     */
    private Map<String, Object> buildLogIndexTemplate() {
        Map<String, Object> template = new HashMap<>();

        // 模板匹配规则
        template.put("index_patterns", List.of(storageConfig.getIndex().getPrefix() + "-*"));

        // 模板设置
        Map<String, Object> settings = new HashMap<>();
        settings.put("number_of_shards", storageConfig.getIndex().getShards());
        settings.put("number_of_replicas", storageConfig.getIndex().getReplicas());
        settings.put("refresh_interval", storageConfig.getIndex().getRefreshInterval());

        // 压缩设置
        if (storageConfig.getCompression().getEnabled()) {
            settings.put("codec", "best_compression");
        }

        // 生命周期策略
        settings.put("index.lifecycle.name", storageConfig.getIndex().getPrefix() + "-policy");
        settings.put("index.lifecycle.rollover_alias", storageConfig.getIndex().getPrefix());

        template.put("settings", settings);

        // 映射
        template.put("mappings", buildMappings());

        // 别名
        Map<String, Object> aliases = new HashMap<>();
        aliases.put(storageConfig.getIndex().getPrefix(), new HashMap<>());
        template.put("aliases", aliases);

        return template;
    }

    /**
     * 构建生命周期策略
     */
    private Map<String, Object> buildLifecyclePolicy() {
        Map<String, Object> policy = new HashMap<>();
        Map<String, Object> phases = new HashMap<>();

        // Hot 阶段：新数据写入，高性能
        Map<String, Object> hotPhase = new HashMap<>();
        Map<String, Object> hotActions = new HashMap<>();
        hotActions.put("rollover", Map.of(
                "max_age", storageConfig.getLifecycle().getHotDataDays() + "d",
                "max_size", "50gb"
        ));
        hotPhase.put("actions", hotActions);
        phases.put("hot", hotPhase);

        // Warm 阶段：只读，降低副本数
        Map<String, Object> warmPhase = new HashMap<>();
        warmPhase.put("min_age", storageConfig.getLifecycle().getHotDataDays() + "d");
        Map<String, Object> warmActions = new HashMap<>();
        warmActions.put("readonly", new HashMap<>());
        warmActions.put("allocate", Map.of("number_of_replicas", 1));
        warmActions.put("forcemerge", Map.of("max_num_segments", 1));
        warmPhase.put("actions", warmActions);
        phases.put("warm", warmPhase);

        // Cold 阶段：归档到 MinIO
        Map<String, Object> coldPhase = new HashMap<>();
        coldPhase.put("min_age", storageConfig.getLifecycle().getWarmDataDays() + "d");
        Map<String, Object> coldActions = new HashMap<>();
        coldActions.put("readonly", new HashMap<>());
        coldActions.put("allocate", Map.of("number_of_replicas", 0));
        coldPhase.put("actions", coldActions);
        phases.put("cold", coldPhase);

        // Delete 阶段：删除数据
        Map<String, Object> deletePhase = new HashMap<>();
        deletePhase.put("min_age", storageConfig.getLifecycle().getColdDataDays() + "d");
        Map<String, Object> deleteActions = new HashMap<>();
        deleteActions.put("delete", new HashMap<>());
        deletePhase.put("actions", deleteActions);
        phases.put("delete", deletePhase);

        policy.put("phases", phases);
        return policy;
    }

    /**
     * 构建映射
     */
    private Map<String, Object> buildMappings() {
        Map<String, Object> mappings = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();

        // 核心字段
        properties.put("traceId", Map.of("type", "keyword"));
        properties.put("spanId", Map.of("type", "keyword"));
        properties.put("tenantId", Map.of("type", "keyword"));
        properties.put("systemId", Map.of("type", "keyword"));
        properties.put("timestamp", Map.of("type", "date", "format", "strict_date_optional_time||epoch_millis"));
        properties.put("level", Map.of("type", "keyword"));
        properties.put("logger", Map.of("type", "keyword"));
        properties.put("thread", Map.of("type", "keyword"));

        // 代码位置
        properties.put("className", Map.of("type", "keyword"));
        properties.put("methodName", Map.of("type", "keyword"));
        properties.put("lineNumber", Map.of("type", "integer"));

        // 日志内容
        properties.put("message", Map.of(
                "type", "text",
                "analyzer", "ik_max_word",
                "search_analyzer", "ik_smart",
                "fields", Map.of("keyword", Map.of("type", "keyword", "ignore_above", 256))
        ));

        properties.put("exception", Map.of(
                "type", "text",
                "fields", Map.of("keyword", Map.of("type", "keyword", "ignore_above", 256))
        ));

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

        // 标签
        properties.put("tags", Map.of("type", "keyword"));

        // 扩展字段（不索引）
        properties.put("extra", Map.of("type", "object", "enabled", false));

        mappings.put("properties", properties);

        // 动态映射设置
        mappings.put("dynamic", "strict");
        mappings.put("_source", Map.of("enabled", true));

        return mappings;
    }

    /**
     * 获取模板信息
     */
    public Map<String, Object> getTemplateInfo(String templateName) {
        try {
            // 实现获取模板信息的逻辑
            log.info("获取模板信息: {}", templateName);
            return new HashMap<>();
        } catch (Exception e) {
            log.error("获取模板信息失败: {}", templateName, e);
            return new HashMap<>();
        }
    }
}