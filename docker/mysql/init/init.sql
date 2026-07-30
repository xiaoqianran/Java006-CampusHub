SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `shiqian_user`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `shiqian_resource`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `shiqian_user`;

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键',
    `username` VARCHAR(50) NOT NULL COMMENT '登录用户名',
    `password` VARCHAR(200) NOT NULL COMMENT 'BCrypt密码',
    `nickname` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '昵称',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    `token_version` BIGINT NOT NULL DEFAULT 0 COMMENT '令牌安全版本',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_sys_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(500) DEFAULT NULL,
    `system_role` TINYINT NOT NULL DEFAULT 0,
    `super_admin` TINYINT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色';

CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id` BIGINT AUTO_INCREMENT,
    `code` VARCHAR(100) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(500) DEFAULT NULL,
    `system_permission` TINYINT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_permission_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` BIGINT AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_role` (`user_id`, `role_id`),
    KEY `idx_sys_user_role_role` (`role_id`),
    CONSTRAINT `fk_sys_user_role_user` FOREIGN KEY (`user_id`)
        REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_sys_user_role_role` FOREIGN KEY (`role_id`)
        REFERENCES `sys_role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `id` BIGINT AUTO_INCREMENT,
    `role_id` BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_role_permission` (`role_id`, `permission_id`),
    KEY `idx_sys_role_permission_permission` (`permission_id`),
    CONSTRAINT `fk_sys_role_permission_role` FOREIGN KEY (`role_id`)
        REFERENCES `sys_role` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_sys_role_permission_permission` FOREIGN KEY (`permission_id`)
        REFERENCES `sys_permission` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联';

INSERT INTO `sys_role`
    (`id`, `code`, `name`, `description`, `system_role`, `super_admin`, `status`, `deleted`)
VALUES
    (1, 'USER', '普通用户', '日常资源权限', 1, 0, 1, 0),
    (2, 'ADMIN', '内容管理员', '用户管理和资源审核', 1, 0, 1, 0),
    (3, 'SUPER_ADMIN', '超级管理员', '角色权限与平台全部管理能力', 1, 1, 1, 0)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `description` = VALUES(`description`),
    `status` = 1,
    `deleted` = 0;

INSERT INTO `sys_permission`
    (`id`, `code`, `name`, `description`, `system_permission`, `status`, `deleted`)
VALUES
    (1, 'resource:read', '资源查看', '查看资源及详情', 1, 1, 0),
    (2, 'resource:download', '资源下载', '下载资源附件', 1, 1, 0),
    (3, 'resource:favorite', '资源收藏', '收藏和取消收藏', 1, 1, 0),
    (4, 'resource:create', '资源创建', '发布新资源', 1, 1, 0),
    (5, 'resource:update', '资源更新', '更新本人资源', 1, 1, 0),
    (6, 'resource:delete', '资源删除', '删除本人资源', 1, 1, 0),
    (7, 'resource:audit', '资源审核', '审核、下架和恢复资源', 1, 1, 0),
    (8, 'user:manage', '用户管理', '启禁用用户和分配普通角色', 1, 1, 0),
    (9, 'rbac:manage', '角色权限管理', '维护角色、权限和多角色关系', 1, 1, 0)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `description` = VALUES(`description`),
    `status` = 1,
    `deleted` = 0;

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission` WHERE `id` BETWEEN 1 AND 6;

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 2, `id` FROM `sys_permission` WHERE `id` BETWEEN 1 AND 8;

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 3, `id` FROM `sys_permission`;

USE `shiqian_resource`;

-- 资源主表
CREATE TABLE IF NOT EXISTS `t_resource` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '上传者用户ID',
    `title` VARCHAR(200) NOT NULL COMMENT '资源标题',
    `description` VARCHAR(1000) DEFAULT NULL COMMENT '资源描述（兼容旧数据）',
    `summary` VARCHAR(500) DEFAULT NULL COMMENT '资源摘要',
    `content_markdown` LONGTEXT DEFAULT NULL COMMENT 'Markdown 正文',
    `content_type` VARCHAR(30) DEFAULT 'ARTICLE' COMMENT '内容类型 ARTICLE/FILE/MIXED',
    `content_scene` VARCHAR(30) NOT NULL DEFAULT 'SHARE' COMMENT '内容频道 BLOG/GALLERY/SHARE',
    `tags` VARCHAR(500) DEFAULT NULL COMMENT '可选自由标签，逗号分隔',
    `external_source` VARCHAR(50) DEFAULT NULL COMMENT '外部内容来源',
    `external_id` VARCHAR(128) DEFAULT NULL COMMENT '外部来源唯一标识',
    `category_id` BIGINT DEFAULT NULL COMMENT '分类ID',
    `file_url` VARCHAR(500) DEFAULT NULL COMMENT '文件地址（可选，文字资源可为空）',
    `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `file_type` VARCHAR(100) DEFAULT NULL COMMENT '文件MIME类型或扩展名',
    `download_count` INT NOT NULL DEFAULT 0 COMMENT '下载次数',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览次数',
    `version` INT NOT NULL DEFAULT 1 COMMENT '版本号',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '资源状态（0=待审核, 1=已发布, 2=待修改, 3=已拒绝, 4=已下架）',
    `review_reason` VARCHAR(500) DEFAULT NULL COMMENT '审核意见',
    `reviewer_id` BIGINT DEFAULT NULL COMMENT '审核人ID',
    `review_time` DATETIME DEFAULT NULL COMMENT '最近审核时间',
    `offline_reason` VARCHAR(500) DEFAULT NULL COMMENT '下架原因',
    `published_time` DATETIME DEFAULT NULL COMMENT '发布时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=正常, 1=已删除）',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_external_content` (`external_source`, `external_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_content_scene` (`content_scene`),
    INDEX `idx_category_id` (`category_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='资源主表';

-- 资源附件表（第二阶段）
CREATE TABLE IF NOT EXISTS `t_resource_attachment` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键',
    `resource_id` BIGINT NOT NULL COMMENT '所属资源ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_url` VARCHAR(500) NOT NULL COMMENT '文件访问地址（对象存储或本地路径）',
    `file_size` BIGINT DEFAULT 0 COMMENT '文件大小（字节）',
    `file_type` VARCHAR(100) DEFAULT NULL COMMENT '扩展名或MIME简写',
    `mime_type` VARCHAR(100) DEFAULT NULL COMMENT '完整MIME类型',
    `asset_kind` VARCHAR(30) DEFAULT 'FILE' COMMENT '资产类型：IMAGE/VIDEO/DOCUMENT/ARCHIVE/CODE/OTHER',
    `usage_type` VARCHAR(30) DEFAULT 'ATTACHMENT' COMMENT '用途：ATTACHMENT/INLINE/COVER',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',

    PRIMARY KEY (`id`),
    INDEX `idx_resource_id` (`resource_id`),
    INDEX `idx_usage_type` (`usage_type`),
    INDEX `idx_asset_kind` (`asset_kind`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='资源附件表';

CREATE TABLE IF NOT EXISTS `t_stored_object` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `public_id` CHAR(36) NOT NULL,
    `owner_id` BIGINT NOT NULL,
    `resource_id` BIGINT DEFAULT NULL,
    `object_key` VARCHAR(500) NOT NULL,
    `original_name` VARCHAR(255) NOT NULL,
    `storage_provider` VARCHAR(30) NOT NULL,
    `bucket_name` VARCHAR(100) DEFAULT NULL,
    `file_size` BIGINT NOT NULL,
    `extension` VARCHAR(20) NOT NULL,
    `mime_type` VARCHAR(100) NOT NULL,
    `asset_kind` VARCHAR(30) NOT NULL,
    `status` VARCHAR(30) NOT NULL DEFAULT 'TEMPORARY',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_stored_object_public_id` (`public_id`),
    UNIQUE KEY `uk_stored_object_key` (`object_key`),
    INDEX `idx_stored_object_owner_status` (`owner_id`, `status`),
    INDEX `idx_stored_object_resource` (`resource_id`, `status`),
    INDEX `idx_stored_object_cleanup` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一对象存储元数据';

CREATE TABLE IF NOT EXISTS `t_user_storage_quota` (
    `owner_id` BIGINT PRIMARY KEY,
    `used_bytes` BIGINT NOT NULL DEFAULT 0,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户持久化存储配额';

CREATE TABLE IF NOT EXISTS `t_sensitive_word` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `word` VARCHAR(100) NOT NULL,
    `level` TINYINT NOT NULL DEFAULT 2,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_by` BIGINT DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY `uk_sensitive_word` (`word`),
    INDEX `idx_sensitive_word_status` (`status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可热更新敏感词';

CREATE TABLE IF NOT EXISTS `t_content_review_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `resource_id` BIGINT DEFAULT NULL,
    `submitter_id` BIGINT DEFAULT NULL,
    `reviewer_id` BIGINT DEFAULT NULL,
    `review_type` VARCHAR(20) NOT NULL,
    `decision` VARCHAR(30) NOT NULL,
    `matched_words` VARCHAR(1000) DEFAULT NULL,
    `reason` VARCHAR(500) DEFAULT NULL,
    `content_title` VARCHAR(200) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_review_resource` (`resource_id`, `create_time`),
    INDEX `idx_review_type_decision` (`review_type`, `decision`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自动与人工内容审核记录';

CREATE TABLE IF NOT EXISTS `t_category` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `parent_id` BIGINT NOT NULL DEFAULT 0,
    `name` VARCHAR(100) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `icon` VARCHAR(255) DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源分类';

CREATE TABLE IF NOT EXISTS `t_tag` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY `uk_tag_name` (`name`),
    INDEX `idx_tag_status` (`status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源标签';

CREATE TABLE IF NOT EXISTS `t_resource_category` (
    `resource_id` BIGINT NOT NULL,
    `category_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`resource_id`, `category_id`),
    INDEX `idx_resource_category_category` (`category_id`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源分类关系';

CREATE TABLE IF NOT EXISTS `t_resource_tag` (
    `resource_id` BIGINT NOT NULL,
    `tag_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`resource_id`, `tag_id`),
    INDEX `idx_resource_tag_tag` (`tag_id`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源标签关系';

CREATE TABLE IF NOT EXISTS `t_resource_version` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `resource_id` BIGINT NOT NULL,
    `version_number` INT NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `summary` VARCHAR(500) DEFAULT NULL,
    `description` VARCHAR(1000) DEFAULT NULL,
    `markdown_content` LONGTEXT DEFAULT NULL,
    `category_id` BIGINT DEFAULT NULL,
    `tags` VARCHAR(500) DEFAULT NULL,
    `content_scene` VARCHAR(30) NOT NULL,
    `resource_type` VARCHAR(30) NOT NULL,
    `file_url` VARCHAR(500) DEFAULT NULL,
    `file_size` BIGINT NOT NULL DEFAULT 0,
    `file_type` VARCHAR(100) DEFAULT NULL,
    `category_ids_json` JSON NOT NULL,
    `tag_names_json` JSON NOT NULL,
    `attachments_json` JSON NOT NULL,
    `change_description` VARCHAR(500) DEFAULT NULL,
    `created_by` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_resource_version` (`resource_id`, `version_number`),
    INDEX `idx_resource_version_time` (`resource_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源版本快照';

CREATE TABLE IF NOT EXISTS `t_favorite` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `resource_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_resource` (`user_id`, `resource_id`),
    INDEX `idx_favorite_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源收藏';

CREATE TABLE IF NOT EXISTS `t_admin_operation_log` (
    `id` BIGINT AUTO_INCREMENT,
    `operator_id` BIGINT NOT NULL DEFAULT 0,
    `operator_name` VARCHAR(100) NOT NULL DEFAULT '系统',
    `operation_type` VARCHAR(100) NOT NULL,
    `target_type` VARCHAR(50) DEFAULT NULL,
    `target_id` BIGINT DEFAULT NULL,
    `detail` VARCHAR(1000) DEFAULT NULL,
    `request_method` VARCHAR(10) DEFAULT NULL,
    `request_uri` VARCHAR(500) DEFAULT NULL,
    `request_ip` VARCHAR(64) DEFAULT NULL,
    `request_params` TEXT DEFAULT NULL,
    `result` VARCHAR(30) DEFAULT NULL,
    `error_message` VARCHAR(1000) DEFAULT NULL,
    `duration_ms` BIGINT DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_admin_log_operator` (`operator_id`),
    INDEX `idx_admin_log_type` (`operation_type`),
    INDEX `idx_admin_log_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作日志';

CREATE TABLE IF NOT EXISTS `t_outbox_event` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `message_id` VARCHAR(64) NOT NULL,
    `event_type` VARCHAR(64) NOT NULL,
    `aggregate_type` VARCHAR(32) NOT NULL DEFAULT 'RESOURCE',
    `aggregate_id` BIGINT NOT NULL,
    `payload` JSON NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `retry_count` INT NOT NULL DEFAULT 0,
    `next_retry_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_error` VARCHAR(1000) DEFAULT NULL,
    `published_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_outbox_message_id` (`message_id`),
    INDEX `idx_outbox_ready` (`status`, `next_retry_time`, `id`),
    INDEX `idx_outbox_aggregate` (`aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本地消息事件表';

CREATE TABLE IF NOT EXISTS `t_mq_consumed_message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `message_id` VARCHAR(64) NOT NULL,
    `consumer_name` VARCHAR(100) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_consumed_message` (`message_id`, `consumer_name`),
    INDEX `idx_consumed_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MQ消费幂等记录';

CREATE TABLE IF NOT EXISTS `t_user_notification` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `message_id` VARCHAR(64) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `notification_type` VARCHAR(50) NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `content` VARCHAR(1000) NOT NULL,
    `related_id` BIGINT DEFAULT NULL,
    `read_flag` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_notification_message` (`message_id`),
    INDEX `idx_notification_user` (`user_id`, `read_flag`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知';
