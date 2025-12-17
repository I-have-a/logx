package com.domidodo.logx.common.constant;


public interface SystemConstant {

    String TENANT_HEADER = "X-Tenant-Id";
    String USER_HEADER = "X-User-Id";
    String REQUEST_ID_HEADER = "X-Request-Id";

    String KAFKA_TOPIC_LOGS_RAW = "logs-raw";

    String REDIS_KEY_PREFIX = "logx:";
    String REDIS_KEY_TOKEN = REDIS_KEY_PREFIX + "token:";

    Integer DEFAULT_PAGE_SIZE = 20;
    Integer MAX_PAGE_SIZE = 100;

    // ================================
    // Kafka Topic 常量（统一管理）
    // ================================

    /**
     * 日志原始数据Topic
     */
    String KAFKA_TOPIC_LOGS = "logx-logs";

    /**
     * 死信队列Topic
     */
    String KAFKA_TOPIC_DLQ = "logx-logs-dlq";

    /**
     * 告警通知Topic
     */
    String KAFKA_TOPIC_ALERTS = "logx-alerts";

    // ================================
    // Redis Key 前缀
    // ================================

    /**
     * API Key 缓存前缀
     */
    String REDIS_KEY_API_KEY = "logx:apikey:";

    /**
     * 限流 Key 前缀
     */
    String REDIS_KEY_RATE_LIMIT = "logx:ratelimit:";

    /**
     * 系统信息缓存前缀
     */
    String REDIS_KEY_SYSTEM = "logx:system:";

    // ================================
    // Elasticsearch 索引配置
    // ================================

    /**
     * 日志索引前缀
     */
    String ES_INDEX_PREFIX = "logx-logs-";

    /**
     * 日志索引别名
     */
    String ES_INDEX_ALIAS = "logx-logs-all";

    /**
     * 索引模板名称
     */
    String ES_TEMPLATE_NAME = "logx-logs-template";

    // ================================
    // 批量处理配置
    // ================================

    /**
     * 默认批量大小
     */
    int DEFAULT_BATCH_SIZE = 100;

    /**
     * 最大批量大小
     */
    int MAX_BATCH_SIZE = 1000;

    /**
     * 批量刷新间隔（毫秒）
     */
    long BATCH_FLUSH_INTERVAL_MS = 5000;

    // ================================
    // 租户相关
    // ================================

    /**
     * 默认租户ID
     */
    String DEFAULT_TENANT_ID = "default";

    /**
     * 租户ID请求头
     */
    String HEADER_TENANT_ID = "X-Tenant-Id";

    /**
     * API Key请求头
     */
    String HEADER_API_KEY = "X-Api-Key";

    // ================================
    // 时间相关
    // ================================

    /**
     * 日志保留天数（默认30天）
     */
    int LOG_RETENTION_DAYS = 30;

    /**
     * 热数据保留天数（默认7天）
     */
    int HOT_DATA_RETENTION_DAYS = 7;

    /**
     * 冷数据保留天数（默认30天）
     */
    int COLD_DATA_RETENTION_DAYS = 30;

    // ================================
    // 系统状态
    // ================================

    /**
     * 系统启用状态
     */
    int SYSTEM_STATUS_ENABLED = 1;

    /**
     * 系统禁用状态
     */
    int SYSTEM_STATUS_DISABLED = 0;

    // ================================
    // 日志级别
    // ================================

    String LEVEL_TRACE = "TRACE";
    String LEVEL_DEBUG = "DEBUG";
    String LEVEL_INFO = "INFO";
    String LEVEL_WARN = "WARN";
    String LEVEL_ERROR = "ERROR";
    String LEVEL_FATAL = "FATAL";

}