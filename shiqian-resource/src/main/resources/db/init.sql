-- 创建数据库
CREATE DATABASE IF NOT EXISTS shiqian_resource DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE shiqian_resource;

-- 资源主表
CREATE TABLE IF NOT EXISTS t_resource (
    id BIGINT AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '上传者用户ID',
    title VARCHAR(200) NOT NULL COMMENT '资源标题',
    description VARCHAR(1000) DEFAULT NULL COMMENT '资源描述',
    category_id BIGINT DEFAULT NULL COMMENT '分类ID',
    file_url VARCHAR(500) NOT NULL COMMENT '文件地址',
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
