package com.domidodo.logx.engine.storage.elasticsearch;

import com.domidodo.logx.engine.storage.config.StorageConfig;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 索引模式匹配器
 */
@Component
public class IndexPatternMatcher {

    private final StorageConfig storageConfig;
    private final DateTimeFormatter indexDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public IndexPatternMatcher(StorageConfig storageConfig) {
        this.storageConfig = storageConfig;
    }

    /**
     * 检查索引是否匹配模式
     */
    public boolean matchesPattern(String indexName) {
        return indexName.startsWith(storageConfig.getIndex().getPrefix() + "-");
    }

    /**
     * 从索引名称提取日期
     */
    public LocalDate extractDate(String indexName) {
        try {
            String[] parts = indexName.split("-");
            if (parts.length >= 5) {
                String datePart = parts[4];  // 格式：yyyy.MM.dd
                return LocalDate.parse(datePart, indexDateFormatter);
            }
        } catch (Exception e) {
            // 解析失败，返回 null
        }
        return null;
    }
}
