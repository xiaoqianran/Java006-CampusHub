SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `shiqian_user`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `shiqian_resource`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `shiqian_user`;

CREATE TABLE IF NOT EXISTS `t_user` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键',
    `username` VARCHAR(50) NOT NULL COMMENT '登录用户名',
    `password` VARCHAR(200) NOT NULL COMMENT 'BCrypt密码',
    `nickname` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '昵称',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像',
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '兼容角色字段',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    `token_version` BIGINT NOT NULL DEFAULT 0 COMMENT '令牌安全版本',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

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
