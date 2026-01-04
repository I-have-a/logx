-- 创建数据库
CREATE DATABASE IF NOT EXISTS `logx` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `logx`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 告警聚合记录表
DROP TABLE IF EXISTS `log_alert_aggregation`;
CREATE TABLE `log_alert_aggregation`
(
    `id`               bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `aggregation_key`  varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '聚合Key',
    `rule_id`          bigint                                                        NOT NULL COMMENT '规则ID',
    `tenant_id`        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '租户ID',
    `system_id`        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '系统ID',
    `suppressed_count` int                                                           NULL DEFAULT 0 COMMENT '抑制次数',
    `first_occurrence` datetime                                                      NULL DEFAULT NULL COMMENT '首次发生时间',
    `last_occurrence`  datetime                                                      NULL DEFAULT NULL COMMENT '最后发生时间',
    `sent`             tinyint(1)                                                    NULL DEFAULT 0 COMMENT '是否已发送汇总',
    `create_time`      datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_aggregation_key` (`aggregation_key` ASC) USING BTREE,
    INDEX `idx_rule_id` (`rule_id` ASC) USING BTREE,
    INDEX `idx_sent` (`sent` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '告警聚合记录表'
  ROW_FORMAT = Dynamic;

-- 告警记录表
DROP TABLE IF EXISTS `log_alert_record`;
CREATE TABLE `log_alert_record`
(
    `id`                bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`         varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '租户ID',
    `system_id`         varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '系统ID',
    `rule_id`           bigint                                                        NULL DEFAULT NULL COMMENT '规则ID',
    `alert_level`       varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '告警级别',
    `alert_type`        varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '告警类型',
    `alert_content`     text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci         NULL COMMENT '告警内容',
    `trigger_time`      datetime                                                      NOT NULL COMMENT '触发时间',
    `status`            varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING/PROCESSING/RESOLVED',
    `is_suppressed`     tinyint                                                       NULL DEFAULT 0 COMMENT '是否被收敛',
    `suppression_group` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '收敛分组key',
    `related_alert_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联的告警ID列表',
    `handle_user`       varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '处理人',
    `handle_time`       datetime                                                      NULL DEFAULT NULL COMMENT '处理时间',
    `handle_remark`     varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '处理备注',
    `create_time`       datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_tenant_system` (`tenant_id` ASC, `system_id` ASC) USING BTREE,
    INDEX `idx_rule_id` (`rule_id` ASC) USING BTREE,
    INDEX `idx_status` (`status` ASC) USING BTREE,
    INDEX `idx_trigger_time` (`trigger_time` ASC) USING BTREE,
    INDEX `idx_suppression_group` (`suppression_group` ASC) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 16
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '告警记录表'
  ROW_FORMAT = Dynamic;

-- 异常规则表
DROP TABLE IF EXISTS `log_exception_rule`;
CREATE TABLE `log_exception_rule`
(
    `id`                           bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`                    varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '租户ID',
    `system_id`                    varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '系统ID',
    `rule_name`                    varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则名称',
    `rule_type`                    varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '规则类型',
    `monitor_target`               varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '监控对象',
    `monitor_metric`               varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '监控指标',
    `condition_operator`           varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '条件操作符',
    `condition_value`              varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '条件值',
    `rule_config`                  text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci         NULL COMMENT '规则扩展配置(JSON)',
    `time_window`                  int                                                           NULL DEFAULT 60 COMMENT '时间窗口(秒)，用于批量操作规则',
    `trigger_count`                int                                                           NULL DEFAULT 0 COMMENT '触发次数统计',
    `last_trigger_time`            datetime                                                      NULL DEFAULT NULL COMMENT '最后触发时间',
    `is_enabled_alert_suppression` tinyint                                                       NULL DEFAULT 0 COMMENT '是否启用告警收敛',
    `alert_suppression_minutes`    int                                                           NULL DEFAULT 60 COMMENT '告警收敛时间(分钟)',
    `alert_level`                  varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '告警级别',
    `silence_period`               int                                                           NULL DEFAULT 300 COMMENT '静默时长（秒），默认5分钟',
    `silence_scope`                varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT 'RULE' COMMENT '静默粒度：RULE/TARGET/USER',
    `allow_escalation`             tinyint(1)                                                    NULL DEFAULT 1 COMMENT '是否允许升级突破静默期',
    `enable_aggregation`           tinyint(1)                                                    NULL DEFAULT 0 COMMENT '是否启用告警聚合',
    `status`                       tinyint                                                       NULL DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
    `create_time`                  datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`                  datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_tenant_system` (`tenant_id` ASC, `system_id` ASC) USING BTREE,
    INDEX `idx_rule_type` (`rule_type` ASC) USING BTREE,
    INDEX `idx_status` (`status` ASC) USING BTREE,
    INDEX `idx_last_trigger` (`last_trigger_time` ASC) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 14
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '异常规则表'
  ROW_FORMAT = Dynamic;

-- 通知配置表
DROP TABLE IF EXISTS `log_notification_config`;
CREATE TABLE `log_notification_config`
(
    `id`             bigint                                                       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`      varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '租户ID',
    `system_id`      varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '系统ID',
    `channel_type`   varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '通知渠道：EMAIL/SMS/WEBHOOK/INTERNAL',
    `channel_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci        NULL COMMENT '渠道配置(JSON)',
    `receivers`      text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci        NULL COMMENT '接收人列表(JSON)',
    `enabled`        tinyint                                                      NULL DEFAULT 1 COMMENT '是否启用',
    `create_time`    datetime                                                     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime                                                     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_tenant_system` (`tenant_id` ASC, `system_id` ASC) USING BTREE,
    INDEX `idx_channel_type` (`channel_type` ASC) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 3
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '通知配置表'
  ROW_FORMAT = Dynamic;

-- 规则执行历史记录
DROP TABLE IF EXISTS `log_rule_execution_history`;
CREATE TABLE `log_rule_execution_history`
(
    `id`               bigint                                                       NOT NULL AUTO_INCREMENT,
    `rule_id`          bigint                                                       NOT NULL COMMENT '规则ID',
    `tenant_id`        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户ID',
    `system_id`        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系统ID',
    `execution_time`   datetime                                                     NOT NULL COMMENT '执行时间',
    `log_count`        int                                                          NULL DEFAULT 0 COMMENT '检测日志数',
    `match_count`      int                                                          NULL DEFAULT 0 COMMENT '匹配数',
    `alert_count`      int                                                          NULL DEFAULT 0 COMMENT '告警数',
    `avg_execution_ms` bigint                                                       NULL DEFAULT NULL COMMENT '平均执行耗时(毫秒)',
    `create_time`      datetime                                                     NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_rule_id` (`rule_id` ASC) USING BTREE,
    INDEX `idx_execution_time` (`execution_time` ASC) USING BTREE,
    INDEX `idx_tenant_system` (`tenant_id` ASC, `system_id` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '规则执行历史记录'
  ROW_FORMAT = Dynamic;

-- 规则静默状态表
DROP TABLE IF EXISTS `log_rule_silence_state`;
CREATE TABLE `log_rule_silence_state`
(
    `id`               bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `silence_key`      varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '静默Key',
    `rule_id`          bigint                                                        NOT NULL COMMENT '规则ID',
    `tenant_id`        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '租户ID',
    `system_id`        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '系统ID',
    `last_alert_time`  datetime                                                      NOT NULL COMMENT '最后告警时间',
    `last_alert_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '最后告警级别',
    `suppressed_count` int                                                           NULL DEFAULT 0 COMMENT '被抑制次数',
    `expire_time`      datetime                                                      NULL DEFAULT NULL COMMENT '过期时间',
    `create_time`      datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_silence_key` (`silence_key` ASC) USING BTREE,
    INDEX `idx_rule_id` (`rule_id` ASC) USING BTREE,
    INDEX `idx_expire_time` (`expire_time` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '规则静默状态表'
  ROW_FORMAT = Dynamic;

-- 规则状态快照（用于恢复）
DROP TABLE IF EXISTS `log_rule_state_snapshot`;
CREATE TABLE `log_rule_state_snapshot`
(
    `id`            bigint                                                        NOT NULL AUTO_INCREMENT,
    `rule_id`       bigint                                                        NOT NULL COMMENT '规则ID',
    `state_key`     varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态key',
    `state_type`    varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '状态类型:CONTINUOUS/BATCH',
    `state_value`   text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         NULL COMMENT '状态值(JSON)',
    `snapshot_time` datetime                                                      NOT NULL COMMENT '快照时间',
    `expire_time`   datetime                                                      NULL DEFAULT NULL COMMENT '过期时间',
    `create_time`   datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_state_key` (`state_key` ASC) USING BTREE,
    INDEX `idx_rule_id` (`rule_id` ASC) USING BTREE,
    INDEX `idx_state_key` (`state_key` ASC) USING BTREE,
    INDEX `idx_expire_time` (`expire_time` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '规则状态快照（用于恢复）'
  ROW_FORMAT = Dynamic;

-- 系统表
DROP TABLE IF EXISTS `sys_system`;
CREATE TABLE `sys_system`
(
    `id`          bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `system_id`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '系统ID',
    `tenant_id`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '租户ID',
    `system_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '系统名称',
    `system_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '系统类型',
    `api_key`     varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'API密钥',
    `status`      tinyint                                                       NULL DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
    `create_time` datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_system_id` (`system_id` ASC) USING BTREE,
    UNIQUE INDEX `uk_api_key` (`api_key` ASC) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id` ASC) USING BTREE,
    INDEX `idx_status` (`status` ASC) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 4
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统表'
  ROW_FORMAT = Dynamic;

-- 租户表
DROP TABLE IF EXISTS `sys_tenant`;
CREATE TABLE `sys_tenant`
(
    `id`             bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`      varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '租户ID',
    `tenant_name`    varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '租户名称',
    `contact_name`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '联系人',
    `contact_phone`  varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '联系电话',
    `contact_email`  varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系邮箱',
    `status`         tinyint                                                       NULL DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
    `expire_time`    datetime                                                      NULL DEFAULT NULL COMMENT '过期时间',
    `max_systems`    int                                                           NULL DEFAULT 10 COMMENT '最大系统数',
    `max_storage_gb` int                                                           NULL DEFAULT 100 COMMENT '最大存储空间(GB)',
    `create_time`    datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_tenant_id` (`tenant_id` ASC) USING BTREE,
    INDEX `idx_status` (`status` ASC) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 3
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '租户表'
  ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;


-- 插入测试数据
-- 租户
INSERT INTO `sys_tenant` (`tenant_id`, `tenant_name`, `contact_name`, `contact_phone`, `contact_email`, `status`,
                          `max_systems`, `max_storage_gb`)
VALUES ('company_a', 'A公司', '张三', '13800138000', 'zhangsan@company-a.com', 1, 10, 100),
       ('company_b', 'B公司', '李四', '13800138001', 'lisi@company-b.com', 1, 5, 50);

-- 系统
INSERT INTO `sys_system` (`system_id`, `tenant_id`, `system_name`, `system_type`, `api_key`, `status`)
VALUES ('erp_system', 'company_a', 'ERP管理系统', 'BUSINESS', 'sk_test_key_001', 1),
       ('crm_system', 'company_a', 'CRM客户系统', 'BUSINESS', 'sk_test_key_002', 1),
       ('wms_system', 'company_b', '仓储管理系统', 'BUSINESS', 'sk_test_key_003', 1);

-- 预设规则
INSERT INTO `log_exception_rule` (`tenant_id`, `system_id`, `rule_name`, `rule_type`, `monitor_metric`,
                                  `condition_operator`, `condition_value`, `alert_level`, `status`)
VALUES ('company_a', 'erp_system', '响应时间异常', 'RESPONSE_TIME', 'responseTime', '>', '3000', 'WARNING', 1),
       ('company_a', 'erp_system', '连续失败告警', 'CONTINUOUS_FAILURE', 'failCount', '>', '5', 'CRITICAL', 1),
       ('company_a', 'erp_system', '错误率异常', 'ERROR_RATE', 'errorRate', '>', '10', 'CRITICAL', 1),
       ('company_b', 'wms_system', '响应时间异常', 'RESPONSE_TIME', 'responseTime', '>', '3000', 'WARNING', 1);

-- 通知配置
INSERT INTO `log_notification_config` (`tenant_id`, `system_id`, `channel_type`, `channel_config`, `receivers`,
                                       `enabled`)
VALUES ('company_a', 'erp_system', 'EMAIL', '{"smtpHost":"smtp.example.com","smtpPort":465}',
        '[{"name":"张三","email":"zhangsan@company-a.com"}]', 1),
       ('company_b', 'wms_system', 'EMAIL', '{"smtpHost":"smtp.example.com","smtpPort":465}',
        '[{"name":"李四","email":"lisi@company-b.com"}]', 1);