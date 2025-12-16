package com.domidodo.logx.engine.storage.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 存储配置类
 */
@Data
@Configuration
@EnableScheduling
@ConfigurationProperties(prefix = "logx.storage")
public class StorageConfig {

    /**
     * 索引配置
     */
    private IndexConfig index = new IndexConfig();

    /**
     * 生命周期配置
     */
    private LifecycleConfig lifecycle = new LifecycleConfig();

    /**
     * 压缩配置
     */
    private CompressionConfig compression = new CompressionConfig();

    /**
     * 批量操作配置
     */
    private BulkConfig bulk = new BulkConfig();

    @Data
    public static class IndexConfig {
        private String prefix = "logx-logs";
        private Integer shards = 5;
        private Integer replicas = 1;
        private String refreshInterval = "5s";
    }

    @Data
    public static class LifecycleConfig {
        private Integer hotDataDays = 7;
        private Integer warmDataDays = 30;
        private Integer coldDataDays = 90;
        private Boolean cleanupEnabled = true;
        private String cleanupCron = "0 0 2 * * ?";
        private Boolean archiveEnabled = true;
        private String archiveCron = "0 0 3 * * ?";
    }

    @Data
    public static class CompressionConfig {
        private Boolean enabled = true;
        private String algorithm = "gzip";
        private Integer level = 6;
    }

    @Data
    public static class BulkConfig {
        private Integer size = 1000;
        private String flushInterval = "10s";
        private Integer concurrentRequests = 2;
    }
}

/**
 * MinIO 配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
class MinioConfig {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String region;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region(region)
                .build();
    }
}