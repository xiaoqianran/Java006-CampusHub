CREATE TABLE IF NOT EXISTS t_stored_object (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL,
    owner_id BIGINT NOT NULL,
    resource_id BIGINT DEFAULT NULL,
    object_key VARCHAR(500) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_provider VARCHAR(30) NOT NULL,
    bucket_name VARCHAR(100) DEFAULT NULL,
    file_size BIGINT NOT NULL,
    extension VARCHAR(20) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    asset_kind VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'TEMPORARY',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_stored_object_public_id (public_id),
    UNIQUE KEY uk_stored_object_key (object_key),
    INDEX idx_stored_object_owner_status (owner_id, status),
    INDEX idx_stored_object_resource (resource_id, status),
    INDEX idx_stored_object_cleanup (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='统一对象存储元数据';

CREATE TABLE IF NOT EXISTS t_user_storage_quota (
    owner_id BIGINT PRIMARY KEY,
    used_bytes BIGINT NOT NULL DEFAULT 0,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户持久化存储配额';
