-- 创建数据库
CREATE DATABASE IF NOT EXISTS `logx` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `logx`;

-- 租户表
CREATE TABLE IF NOT EXISTS `sys_tenant`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`      VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `tenant_name`    VARCHAR(128) NOT NULL COMMENT '租户名称',
    `contact_name`   VARCHAR(64) COMMENT '联系人',
    `contact_phone`  VARCHAR(32) COMMENT '联系电话',
    `contact_email`  VARCHAR(128) COMMENT '联系邮箱',
    `status`         TINYINT  DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
    `expire_time`    DATETIME COMMENT '过期时间',
    `max_systems`    INT      DEFAULT 10 COMMENT '最大系统数',
    `max_storage_gb` INT      DEFAULT 100 COMMENT '最大存储空间(GB)',
    `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='租户表';

-- 系统表
CREATE TABLE IF NOT EXISTS `sys_system`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `system_id`   VARCHAR(64)  NOT NULL COMMENT '系统ID',
    `tenant_id`   VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `system_name` VARCHAR(128) NOT NULL COMMENT '系统名称',
    `system_type` VARCHAR(32) COMMENT '系统类型',
    `api_key`     VARCHAR(128) NOT NULL COMMENT 'API密钥',
    `status`      TINYINT  DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_system_id` (`system_id`),
    UNIQUE KEY `uk_api_key` (`api_key`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统表';

-- 异常规则表
CREATE TABLE IF NOT EXISTS `log_exception_rule`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`          VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `system_id`          VARCHAR(64)  NOT NULL COMMENT '系统ID',
    `rule_name`          VARCHAR(128) NOT NULL COMMENT '规则名称',
    `rule_type`          VARCHAR(32)  NOT NULL COMMENT '规则类型',
    `monitor_target`     VARCHAR(256) COMMENT '监控对象',
    `monitor_metric`     VARCHAR(64) COMMENT '监控指标',
    `condition_operator` VARCHAR(16) COMMENT '条件操作符',
    `condition_value`    VARCHAR(256) COMMENT '条件值',
    `alert_level`        VARCHAR(16) COMMENT '告警级别',
    `status`             TINYINT  DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
    `create_time`        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_system` (`tenant_id`, `system_id`),
    KEY `idx_rule_type` (`rule_type`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='异常规则表';

-- 通知配置表
CREATE TABLE IF NOT EXISTS `log_notification_config`
(
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`      VARCHAR(64) NOT NULL COMMENT '租户ID',
    `system_id`      VARCHAR(64) NOT NULL COMMENT '系统ID',
    `channel_type`   VARCHAR(32) NOT NULL COMMENT '通知渠道：EMAIL/SMS/WEBHOOK/INTERNAL',
    `channel_config` TEXT COMMENT '渠道配置(JSON)',
    `receivers`      TEXT COMMENT '接收人列表(JSON)',
    `enabled`        TINYINT  DEFAULT 1 COMMENT '是否启用',
    `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_system` (`tenant_id`, `system_id`),
    KEY `idx_channel_type` (`channel_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='通知配置表';

-- 告警记录表
CREATE TABLE IF NOT EXISTS `log_alert_record`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`     VARCHAR(64) NOT NULL COMMENT '租户ID',
    `system_id`     VARCHAR(64) NOT NULL COMMENT '系统ID',
    `rule_id`       BIGINT COMMENT '规则ID',
    `alert_level`   VARCHAR(16) NOT NULL COMMENT '告警级别',
    `alert_type`    VARCHAR(32) NOT NULL COMMENT '告警类型',
    `alert_content` TEXT COMMENT '告警内容',
    `trigger_time`  DATETIME    NOT NULL COMMENT '触发时间',
    `status`        VARCHAR(16) DEFAULT 'PENDING' COMMENT '处理状态：PENDING/PROCESSING/RESOLVED',
    `handle_user`   VARCHAR(64) COMMENT '处理人',
    `handle_time`   DATETIME COMMENT '处理时间',
    `handle_remark` VARCHAR(512) COMMENT '处理备注',
    `create_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_system` (`tenant_id`, `system_id`),
    KEY `idx_rule_id` (`rule_id`),
    KEY `idx_status` (`status`),
    KEY `idx_trigger_time` (`trigger_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='告警记录表';

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