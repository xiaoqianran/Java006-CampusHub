-- 创建数据库
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS shiqian_resource DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE shiqian_resource;

-- 资源主表
CREATE TABLE IF NOT EXISTS t_resource (
    id BIGINT AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '上传者用户ID',
    title VARCHAR(200) NOT NULL COMMENT '资源标题',
    description VARCHAR(1000) DEFAULT NULL COMMENT '资源描述（兼容旧数据）',
    summary VARCHAR(500) DEFAULT NULL COMMENT '资源摘要',
    content_markdown LONGTEXT DEFAULT NULL COMMENT 'Markdown 正文',
    content_type VARCHAR(30) DEFAULT 'ARTICLE' COMMENT '内容类型 ARTICLE/FILE/MIXED',
    content_scene VARCHAR(30) NOT NULL DEFAULT 'SHARE' COMMENT '内容频道 BLOG/GALLERY/SHARE',
    tags VARCHAR(500) DEFAULT NULL COMMENT '可选自由标签，逗号分隔',
    category_id BIGINT DEFAULT NULL COMMENT '分类ID',
    file_url VARCHAR(500) DEFAULT NULL COMMENT '文件地址（可选，文字资源可为空）',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    file_type VARCHAR(100) DEFAULT NULL COMMENT '文件MIME类型或扩展名',
    download_count INT NOT NULL DEFAULT 0 COMMENT '下载次数',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '资源状态（0=待审核, 1=已发布, 2=待修改, 3=已拒绝, 4=已下架）',
    review_reason VARCHAR(500) DEFAULT NULL COMMENT '审核意见',
    reviewer_id BIGINT DEFAULT NULL COMMENT '审核人ID',
    review_time DATETIME DEFAULT NULL COMMENT '最近审核时间',
    offline_reason VARCHAR(500) DEFAULT NULL COMMENT '下架原因',
    published_time DATETIME DEFAULT NULL COMMENT '发布时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=正常, 1=已删除）',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_content_scene (content_scene),
    INDEX idx_category_id (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源主表';

-- 资源附件表（第二阶段）
CREATE TABLE IF NOT EXISTS t_resource_attachment (
    id BIGINT AUTO_INCREMENT COMMENT '主键',
    resource_id BIGINT NOT NULL COMMENT '所属资源ID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_url VARCHAR(500) NOT NULL COMMENT '文件访问地址（对象存储或本地路径）',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小（字节）',
    file_type VARCHAR(100) COMMENT '扩展名或MIME简写',
    mime_type VARCHAR(100) COMMENT '完整MIME类型',
    asset_kind VARCHAR(30) DEFAULT 'FILE' COMMENT '资产类型：IMAGE/VIDEO/DOCUMENT/ARCHIVE/CODE/OTHER',
    usage_type VARCHAR(30) DEFAULT 'ATTACHMENT' COMMENT '用途：ATTACHMENT/INLINE/COVER',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (id),
    INDEX idx_resource_id (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源附件表';

-- 资源收藏表
CREATE TABLE IF NOT EXISTS t_favorite (
    id BIGINT AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_resource (user_id, resource_id),
    INDEX idx_resource_id (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源收藏表';

-- 资源分类表
CREATE TABLE IF NOT EXISTS t_category (
    id BIGINT AUTO_INCREMENT COMMENT '主键',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父分类ID，0为根分类',
    name VARCHAR(100) NOT NULL COMMENT '分类名称',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    icon VARCHAR(255) DEFAULT NULL COMMENT '图标URL',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1=启用, 0=禁用）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=正常, 1=已删除）',
    PRIMARY KEY (id),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源分类表';

-- 分类种子数据（UTF-8 / utf8mb4 安全）
-- 如果直接导入时仍出现乱码，请使用：
--   mysql --default-character-set=utf8mb4 -uroot -proot shiqian_resource < .../init.sql
INSERT INTO t_category (id, parent_id, name, sort_order, icon, status, deleted)
VALUES
    (1, 0, '计算机科学', 10, NULL, 1, 0),
    (2, 0, '高等数学', 20, NULL, 1, 0),
    (3, 0, '大学英语', 30, NULL, 1, 0),
    (4, 0, '考研资料', 40, NULL, 1, 0),
    (5, 0, '课程笔记', 50, NULL, 1, 0),
    (6, 0, '实验报告', 60, NULL, 1, 0),
    (7, 0, '竞赛资料', 70, NULL, 1, 0),
    (8, 0, '校园生活', 80, NULL, 1, 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    sort_order = VALUES(sort_order),
    status = VALUES(status),
    deleted = 0;
