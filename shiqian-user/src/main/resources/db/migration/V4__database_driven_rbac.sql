-- 从旧 t_user.role 单角色模型迁移到 sys_* 多角色 RBAC。
-- 执行前请备份 shiqian_user；本脚本面向 MySQL 8。
USE shiqian_user;

SET @has_old_user = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 't_user'
);
SET @has_sys_user = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_user'
);
SET @rename_user_sql = IF(
    @has_old_user = 1 AND @has_sys_user = 0,
    'RENAME TABLE t_user TO sys_user',
    'SELECT 1'
);
PREPARE rename_user_stmt FROM @rename_user_sql;
EXECUTE rename_user_stmt;
DEALLOCATE PREPARE rename_user_stmt;

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    system_role TINYINT NOT NULL DEFAULT 0,
    super_admin TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    system_permission TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_by BIGINT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sys_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id),
    KEY idx_sys_role_permission_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_role
    (code, name, description, system_role, super_admin, status, deleted)
VALUES
    ('USER', '普通用户', '发布、查看、收藏和下载资源', 1, 0, 1, 0),
    ('ADMIN', '内容管理员', '用户管理和资源审核', 1, 0, 1, 0),
    ('SUPER_ADMIN', '超级管理员', '角色权限与平台全部管理能力', 1, 1, 1, 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), description = VALUES(description),
    system_role = VALUES(system_role), super_admin = VALUES(super_admin),
    status = 1, deleted = 0;

INSERT INTO sys_permission
    (code, name, description, system_permission, status, deleted)
VALUES
    ('resource:read', '资源查看', '查看资源及详情', 1, 1, 0),
    ('resource:download', '资源下载', '下载资源附件', 1, 1, 0),
    ('resource:favorite', '资源收藏', '收藏和取消收藏', 1, 1, 0),
    ('resource:create', '资源创建', '发布新资源', 1, 1, 0),
    ('resource:update', '资源更新', '更新本人资源', 1, 1, 0),
    ('resource:delete', '资源删除', '删除本人资源', 1, 1, 0),
    ('resource:audit', '资源审核', '审核、下架和恢复资源', 1, 1, 0),
    ('user:manage', '用户管理', '分页、启禁用和分配普通角色', 1, 1, 0),
    ('rbac:manage', '角色权限管理', '维护角色、权限和用户多角色关系', 1, 1, 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), description = VALUES(description),
    system_permission = VALUES(system_permission), status = 1, deleted = 0;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'resource:read', 'resource:download', 'resource:favorite',
    'resource:create', 'resource:update', 'resource:delete'
) WHERE r.code = 'USER';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
JOIN sys_permission p ON p.code <> 'rbac:manage'
WHERE r.code = 'ADMIN';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SUPER_ADMIN';

-- 先根据旧 role 列迁移关联。旧 ADMIN 成为 SUPER_ADMIN，避免迁移后无人可管理 RBAC。
SET @has_legacy_role = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'role'
);
SET @migrate_roles_sql = IF(
    @has_legacy_role = 1,
    'INSERT IGNORE INTO sys_user_role (user_id, role_id)
     SELECT u.id, r.id
     FROM sys_user u
     JOIN sys_role r
       ON r.code = IF(UPPER(u.role) = ''ADMIN'', ''SUPER_ADMIN'', ''USER'')
     WHERE u.deleted = 0',
    'SELECT 1'
);
PREPARE migrate_roles_stmt FROM @migrate_roles_sql;
EXECUTE migrate_roles_stmt;
DEALLOCATE PREPARE migrate_roles_stmt;

-- 没有关联的存量用户统一补 USER。
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.code = 'USER'
LEFT JOIN sys_user_role ur ON ur.user_id = u.id
WHERE u.deleted = 0 AND ur.id IS NULL;

SET @drop_legacy_role_sql = IF(
    @has_legacy_role = 1,
    'ALTER TABLE sys_user DROP COLUMN role',
    'SELECT 1'
);
PREPARE drop_legacy_role_stmt FROM @drop_legacy_role_sql;
EXECUTE drop_legacy_role_stmt;
DEALLOCATE PREPARE drop_legacy_role_stmt;
