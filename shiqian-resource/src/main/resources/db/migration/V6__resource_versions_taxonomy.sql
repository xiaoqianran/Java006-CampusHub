-- 阶段6：资源版本快照、标签及多分类关系。
CREATE TABLE IF NOT EXISTS t_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '标签名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用，0禁用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tag_name (name),
    INDEX idx_tag_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源标签';

CREATE TABLE IF NOT EXISTS t_resource_category (
    resource_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (resource_id, category_id),
    INDEX idx_resource_category_category (category_id, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源分类关系';

CREATE TABLE IF NOT EXISTS t_resource_tag (
    resource_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (resource_id, tag_id),
    INDEX idx_resource_tag_tag (tag_id, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源标签关系';

CREATE TABLE IF NOT EXISTS t_resource_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500) DEFAULT NULL,
    description VARCHAR(1000) DEFAULT NULL,
    markdown_content LONGTEXT DEFAULT NULL,
    category_id BIGINT DEFAULT NULL COMMENT '旧单分类字段快照',
    tags VARCHAR(500) DEFAULT NULL COMMENT '旧标签字符串快照',
    content_scene VARCHAR(30) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    file_url VARCHAR(500) DEFAULT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    file_type VARCHAR(100) DEFAULT NULL,
    category_ids_json JSON NOT NULL,
    tag_names_json JSON NOT NULL,
    attachments_json JSON NOT NULL COMMENT '附件元数据快照',
    change_description VARCHAR(500) DEFAULT NULL,
    created_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_resource_version (resource_id, version_number),
    INDEX idx_resource_version_time (resource_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源版本快照';

-- 历史资源的单分类关系可以无损回填；标签和版本由服务首次读写时按旧字段惰性迁移。
INSERT IGNORE INTO t_resource_category (resource_id, category_id)
SELECT id, category_id
FROM t_resource
WHERE category_id IS NOT NULL;
