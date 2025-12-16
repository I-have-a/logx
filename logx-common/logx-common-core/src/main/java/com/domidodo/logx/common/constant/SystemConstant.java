package com.domidodo.logx.common.constant;


public interface SystemConstant {

    String TENANT_HEADER = "X-Tenant-Id";
    String USER_HEADER = "X-User-Id";
    String REQUEST_ID_HEADER = "X-Request-Id";

    String KAFKA_TOPIC_LOGS_RAW = "logs-raw";
    String KAFKA_TOPIC_ALERTS = "alerts";

    String REDIS_KEY_PREFIX = "logx:";
    String REDIS_KEY_TOKEN = REDIS_KEY_PREFIX + "token:";
    String REDIS_KEY_RATE_LIMIT = REDIS_KEY_PREFIX + "rate_limit:";

    Integer DEFAULT_PAGE_SIZE = 20;
    Integer MAX_PAGE_SIZE = 100;
}