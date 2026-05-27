-- ================================================
-- TalkAI 数据库初始化脚本
-- 首次启动 MySQL 容器时自动执行
-- ================================================

CREATE DATABASE IF NOT EXISTS talkai
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE talkai;

-- ================================================
-- 用户表
-- ================================================
CREATE TABLE IF NOT EXISTS `user` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
    `username`        VARCHAR(64)  NOT NULL                 COMMENT '用户名',
    `password`        VARCHAR(256) NOT NULL                 COMMENT '密码（BCrypt加密）',
    `nickname`        VARCHAR(64)  DEFAULT NULL             COMMENT '昵称',
    `avatar`          VARCHAR(512) DEFAULT NULL             COMMENT '头像URL',
    `email`           VARCHAR(128) DEFAULT NULL             COMMENT '邮箱',
    `phone`           VARCHAR(32)  DEFAULT NULL             COMMENT '手机号',
    `status`          TINYINT      NOT NULL DEFAULT 1       COMMENT '账号状态：0=禁用, 1=正常',
    `last_login_time` DATETIME     DEFAULT NULL             COMMENT '最后登录时间',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0=正常, 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ================================================
-- 会话表（一次多轮对话 = 一个会话）
-- ================================================
CREATE TABLE IF NOT EXISTS `conversation` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '会话ID',
    `user_id`       BIGINT       NOT NULL                 COMMENT '所属用户ID',
    `title`         VARCHAR(256) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    `model`         VARCHAR(64)  NOT NULL DEFAULT 'deepseek-chat' COMMENT '使用的模型',
    `system_prompt` TEXT         DEFAULT NULL             COMMENT '系统提示词',
    `message_count` INT          NOT NULL DEFAULT 0       COMMENT '消息数量',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0=正常, 1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- ================================================
-- 消息表（会话中的每条对话记录）
-- ================================================
CREATE TABLE IF NOT EXISTS `message` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '消息ID',
    `conversation_id` BIGINT       NOT NULL                 COMMENT '所属会话ID',
    `role`            VARCHAR(16)  NOT NULL                 COMMENT '角色：user=用户, assistant=助手, system=系统',
    `content`         TEXT         NOT NULL                 COMMENT '消息内容',
    `token_count`     INT          DEFAULT 0                COMMENT '消耗Token数',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0=正常, 1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- ================================================
-- 照片修复记录表
-- ================================================
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

-- ================================================
-- 用户配额表
-- ================================================
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

-- ================================================
-- 计费记录表
-- ================================================
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
