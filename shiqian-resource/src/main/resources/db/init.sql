-- 创建数据库
CREATE DATABASE IF NOT EXISTS shiqian_resource DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE shiqian_resource;

-- 资源主表
CREATE TABLE IF NOT EXISTS t_resource (
    id BIGINT AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '上传者用户ID',
    title VARCHAR(200) NOT NULL COMMENT '资源标题',
    description VARCHAR(1000) DEFAULT NULL COMMENT '资源描述（兼容旧数据）',
    summary VARCHAR(500) DEFAULT NULL COMMENT '资源摘要',
    content_markdown LONGTEXT DEFAULT NULL COMMENT 'Markdown 正文',
    content_type VARCHAR(30) DEFAULT 'MARKDOWN' COMMENT '内容类型 MARKDOWN/HTML',
    category_id BIGINT DEFAULT NULL COMMENT '分类ID',
    file_url VARCHAR(500) DEFAULT NULL COMMENT '文件地址（可选，文字资源可为空）',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    file_type VARCHAR(100) DEFAULT NULL COMMENT '文件MIME类型或扩展名',
    download_count INT NOT NULL DEFAULT 0 COMMENT '下载次数',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态（0=待审核, 1=已通过, 2=已拒绝）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=正常, 1=已删除）',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_category_id (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源主表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源附件表';

-- 资源收藏表
CREATE TABLE IF NOT EXISTS t_favorite (
    id BIGINT AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_resource (user_id, resource_id),
    INDEX idx_resource_id (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源收藏表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源分类表';
