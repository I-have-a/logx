package com.domidodo.logx.engine.storage.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.indices.*;
import com.domidodo.logx.engine.storage.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.Map;

/**
 * Elasticsearch 模板管理器（完整实现版）
 * 负责索引模板和生命周期策略的管理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsTemplateManager {

    private final ElasticsearchClient elasticsearchClient;
    private final StorageConfig storageConfig;

    private static final String TEMPLATE_NAME_SUFFIX = "-template";
    private static final String POLICY_NAME_SUFFIX = "-policy";

    /**
     * 初始化索引模板
     */
    @PostConstruct
    public void initIndexTemplate() {
        try {
            log.info("=== 开始初始化 Elasticsearch 索引模板 ===");

            // 1. 创建索引模板
            createLogIndexTemplate();

            // 2. 创建生命周期策略（如果支持）
            try {
                createLifecyclePolicy();
            } catch (Exception e) {
                log.warn("生命周期策略创建失败（可能不支持该功能）: {}", e.getMessage());
            }

            log.info("=== 索引模板初始化完成 ===");
        } catch (Exception e) {
            log.error("索引模板初始化失败", e);
            // 不抛出异常，允许应用继续启动
        }
    }

    /**
     * 创建日志索引模板（完整实现）
     */
    public void createLogIndexTemplate() {
        String templateName = storageConfig.getIndex().getPrefix() + TEMPLATE_NAME_SUFFIX;
        String indexPattern = storageConfig.getIndex().getPrefix() + "-*";

        try {
            log.info("创建索引模板: {}", templateName);

            // 构建模板
            PutIndexTemplateRequest request = PutIndexTemplateRequest.of(t -> t
                    .name(templateName)
                    .indexPatterns(indexPattern)
                    .template(template -> template
                            .settings(buildTemplateSettings())
                            .mappings(buildTemplateMappings())
                            .aliases(storageConfig.getIndex().getPrefix(), a -> a)
                    )
                    .priority(200)
            );

            // 执行创建
            PutIndexTemplateResponse response = elasticsearchClient.indices()
                    .putIndexTemplate(request);

            if (response.acknowledged()) {
                log.info("索引模板创建成功: {}", templateName);
            } else {
                log.warn("索引模板创建未确认: {}", templateName);
            }

        } catch (Exception e) {
            log.error("创建索引模板失败: {}", templateName, e);
            throw new RuntimeException("创建索引模板失败", e);
        }
    }

    /**
     * 构建模板设置
     */
    private IndexSettings buildTemplateSettings() {
        return IndexSettings.of(s -> s
                .numberOfShards(String.valueOf(storageConfig.getIndex().getShards()))
                .numberOfReplicas(String.valueOf(storageConfig.getIndex().getReplicas()))
                .refreshInterval(time -> time.time(storageConfig.getIndex().getRefreshInterval()))
                // 压缩设置
                .codec(storageConfig.getCompression().getEnabled() ? "best_compression" : "default")
                // 其他优化设置
                .maxResultWindow(10000)
        );
    }

    /**
     * 构建模板映射（完整版本）
     */
    private TypeMapping buildTemplateMappings() {
        return TypeMapping.of(m -> m
                .dynamic(DynamicMapping.Strict)  // 严格模式，防止字段爆炸
                .properties(buildMappingProperties())
        );
    }

    /**
     * 构建字段映射
     */
    private Map<String, Property> buildMappingProperties() {
        Map<String, Property> properties = new HashMap<>();

        // 1. 核心字段
        properties.put("traceId", Property.of(p -> p.keyword(k -> k.ignoreAbove(256))));
        properties.put("spanId", Property.of(p -> p.keyword(k -> k.ignoreAbove(256))));
        properties.put("tenantId", Property.of(p -> p.keyword(k -> k.ignoreAbove(128))));
        properties.put("systemId", Property.of(p -> p.keyword(k -> k.ignoreAbove(128))));
        properties.put("systemName", Property.of(p -> p.keyword(k -> k.ignoreAbove(128))));

        // 2. 时间戳（关键字段）
        properties.put("timestamp", Property.of(p -> p.date(d -> d
                .format("strict_date_optional_time||epoch_millis")
        )));

        // 3. 日志级别
        properties.put("level", Property.of(p -> p.keyword(k -> k)));

        // 4. 日志来源
        properties.put("logger", Property.of(p -> p.keyword(k -> k.ignoreAbove(512))));
        properties.put("thread", Property.of(p -> p.keyword(k -> k.ignoreAbove(256))));

        // 5. 代码位置
        properties.put("className", Property.of(p -> p.keyword(k -> k.ignoreAbove(512))));
        properties.put("methodName", Property.of(p -> p.keyword(k -> k.ignoreAbove(256))));
        properties.put("lineNumber", Property.of(p -> p.integer(i -> i)));

        // 6. 日志内容（支持中文分词）
        properties.put("message", Property.of(p -> p.text(t -> t
                .analyzer("ik_max_word")
                .fields("keyword", Property.of(kf -> kf.keyword(k -> k.ignoreAbove(256))))
        )));

        // 7. 异常信息
        properties.put("exception", Property.of(p -> p.text(t -> t
                .fields("keyword", Property.of(kf -> kf.keyword(k -> k.ignoreAbove(256))))
        )));

        // 8. 用户信息
        properties.put("userId", Property.of(p -> p.keyword(k -> k.ignoreAbove(128))));
        properties.put("userName", Property.of(p -> p.keyword(k -> k.ignoreAbove(256))));

        // 9. 业务信息
        properties.put("module", Property.of(p -> p.keyword(k -> k.ignoreAbove(128))));
        properties.put("operation", Property.of(p -> p.keyword(k -> k.ignoreAbove(256))));

        // 10. 请求信息
        properties.put("requestUrl", Property.of(p -> p.keyword(k -> k.ignoreAbove(1024))));
        properties.put("requestMethod", Property.of(p -> p.keyword(k -> k)));
        properties.put("requestParams", Property.of(p -> p.text(t -> t)));
        properties.put("responseTime", Property.of(p -> p.long_(l -> l)));

        // 11. 客户端信息
        properties.put("ip", Property.of(p -> p.ip(i -> i)));
        properties.put("userAgent", Property.of(p -> p.text(t -> t
                .fields("keyword", Property.of(kf -> kf.keyword(k -> k.ignoreAbove(512))))
        )));

        // 12. 标签
        properties.put("tags", Property.of(p -> p.keyword(k -> k)));

        // 13. 扩展字段（不索引，仅存储）
        properties.put("extra", Property.of(p -> p.object(o -> o.enabled(false))));

        return properties;
    }

    /**
     * 创建生命周期策略
     * 注意：需要 X-Pack 许可证
     */
    public void createLifecyclePolicy() {
        String templateName = storageConfig.getIndex().getPrefix() + TEMPLATE_NAME_SUFFIX;
        String indexPattern = storageConfig.getIndex().getPrefix() + "-*";
        String policyName = storageConfig.getIndex().getPrefix() + POLICY_NAME_SUFFIX;

        try {
            log.info("创建生命周期策略: {}", policyName);

            // 这需要 X-Pack 支持，如果没有许可证会失败
            // 构建模板
            PutIndexTemplateRequest request = PutIndexTemplateRequest.of(t -> t
                    .name(templateName)
                    .indexPatterns(indexPattern)
                    .template(template -> template
                            .settings(settings -> settings
                                    .numberOfShards(String.valueOf(storageConfig.getIndex().getShards()))
                                    .numberOfReplicas(String.valueOf(storageConfig.getIndex().getReplicas()))
                                    .refreshInterval(time -> time.time(storageConfig.getIndex().getRefreshInterval()))
                                    // 压缩设置
                                    .codec(storageConfig.getCompression().getEnabled() ? "best_compression" : "default")
                                    // 其他优化设置
                                    .maxResultWindow(10000)
                                    // 应用 ILM 策略
                                    .lifecycle(lifecycle -> lifecycle
                                            .name(policyName)
                                            .rolloverAlias(storageConfig.getIndex().getPrefix())
                                    )
                            )
                            .mappings(buildTemplateMappings())
                            .aliases(storageConfig.getIndex().getPrefix(), a -> a)
                    )
                    .priority(200)
            );
            // 执行创建
            PutIndexTemplateResponse response = elasticsearchClient.indices()
                    .putIndexTemplate(request);

            if (response.acknowledged()) {
                log.info("索引模板创建成功: {}", templateName);
            } else {
                log.warn("索引模板创建未确认: {}", templateName);
            }

        } catch (Exception e) {
            log.warn("创建生命周期策略失败: {}", policyName, e);
            // 不抛出异常，允许继续
        }
    }

    /**
     * 更新索引模板
     */
    public void updateIndexTemplate() {
        String templateName = storageConfig.getIndex().getPrefix() + TEMPLATE_NAME_SUFFIX;

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
            DeleteIndexTemplateRequest request = DeleteIndexTemplateRequest.of(d -> d
                    .name(templateName)
            );

            DeleteIndexTemplateResponse response = elasticsearchClient.indices()
                    .deleteIndexTemplate(request);

            if (response.acknowledged()) {
                log.info("删除索引模板成功: {}", templateName);
            }
        } catch (Exception e) {
            log.warn("删除索引模板失败（可能不存在）: {}", templateName);
        }
    }

    /**
     * 检查模板是否存在
     */
    public boolean templateExists(String templateName) {
        try {
            ExistsIndexTemplateRequest request = ExistsIndexTemplateRequest.of(e -> e
                    .name(templateName)
            );

            return elasticsearchClient.indices().existsIndexTemplate(request).value();
        } catch (Exception e) {
            log.error("检查模板存在性失败: {}", templateName, e);
            return false;
        }
    }

    /**
     * 获取模板信息
     */
    public Map<String, Object> getTemplateInfo(String templateName) {
        Map<String, Object> info = new HashMap<>();

        try {
            GetIndexTemplateRequest request = GetIndexTemplateRequest.of(g -> g
                    .name(templateName)
            );

            GetIndexTemplateResponse response = elasticsearchClient.indices()
                    .getIndexTemplate(request);

            if (!response.indexTemplates().isEmpty()) {
                info.put("exists", true);
                info.put("name", templateName);
                info.put("patterns", response.indexTemplates().get(0).indexTemplate().indexPatterns());
                log.info("获取模板信息成功: {}", templateName);
            } else {
                info.put("exists", false);
            }

        } catch (Exception e) {
            log.error("获取模板信息失败: {}", templateName, e);
            info.put("error", e.getMessage());
        }

        return info;
    }

    /**
     * 验证模板配置
     */
    public boolean validateTemplate() {
        String templateName = storageConfig.getIndex().getPrefix() + TEMPLATE_NAME_SUFFIX;

        try {
            boolean exists = templateExists(templateName);

            if (!exists) {
                log.warn("索引模板不存在: {}", templateName);
                return false;
            }

            Map<String, Object> info = getTemplateInfo(templateName);
            log.info("模板验证通过: {}", info);
            return true;

        } catch (Exception e) {
            log.error("验证模板失败", e);
            return false;
        }
    }
}