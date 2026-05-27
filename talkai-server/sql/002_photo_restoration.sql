-- ================================================
-- AI老照片修复系统 - 增量建表脚本
-- 使用方式: mysql -u root -proot talkai < 002_photo_restoration.sql
-- ================================================

USE talkai;

CREATE TABLE IF NOT EXISTS `photo_restoration` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
    `user_id`         BIGINT       NOT NULL                 COMMENT '所属用户ID',
    `original_url`    VARCHAR(512) NOT NULL                 COMMENT '原始照片存储路径',
    `restored_url`    VARCHAR(512) DEFAULT NULL             COMMENT '修复后照片路径',
    `comparison_url`  VARCHAR(512) DEFAULT NULL             COMMENT '对比图路径',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'UPLOADED' COMMENT '状态: UPLOADED,ANALYZING,REPAIRING,COMPLETED,FAILED',
    `damage_level`    VARCHAR(16)  DEFAULT NULL             COMMENT '破损等级: NONE,SLIGHT,MODERATE,SEVERE',
    `fidelity`        DOUBLE       DEFAULT NULL             COMMENT 'CodeFormer保真度参数 0.3-0.7',
    `colorize`        TINYINT      DEFAULT 0                COMMENT '是否黑白上色: 0=否,1=是',
    `copy_text`       TEXT         DEFAULT NULL             COMMENT 'AI生成的引流文案',
    `error_message`   TEXT         DEFAULT NULL             COMMENT '失败原因',
    `python_job_id`   VARCHAR(128) DEFAULT NULL             COMMENT 'Python服务的异步任务ID',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除: 0=正常, 1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='照片修复记录表';

CREATE TABLE IF NOT EXISTS `user_quota` (
    `id`              BIGINT   NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
    `user_id`         BIGINT   NOT NULL                 COMMENT '用户ID',
    `total_free`      INT      NOT NULL DEFAULT 3       COMMENT '免费修复总次数',
    `used_free`       INT      NOT NULL DEFAULT 0       COMMENT '已使用免费次数',
    `total_paid`      INT      NOT NULL DEFAULT 0       COMMENT '购买的修复总次数',
    `used_paid`       INT      NOT NULL DEFAULT 0       COMMENT '已使用付费次数',
    `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户配额表';

CREATE TABLE IF NOT EXISTS `billing_record` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
    `user_id`         BIGINT        NOT NULL                 COMMENT '用户ID',
    `photo_id`        BIGINT        DEFAULT NULL             COMMENT '关联的修复记录ID',
    `amount`          DECIMAL(10,2) NOT NULL                 COMMENT '金额（元）',
    `payment_method`  VARCHAR(32)   DEFAULT NULL             COMMENT '支付方式',
    `transaction_id`  VARCHAR(128)  DEFAULT NULL             COMMENT '外部支付单号',
    `status`          VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING,PAID,REFUNDED',
    `credits`         INT           NOT NULL DEFAULT 0       COMMENT '购买的修复次数',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_transaction_id` (`transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费记录表';
