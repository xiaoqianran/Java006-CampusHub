-- CampusHub 用户与数据库驱动 RBAC 初始化脚本
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS shiqian_user
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE shiqian_user;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(50) NOT NULL COMMENT '登录用户名',
    password VARCHAR(200) NOT NULL COMMENT 'BCrypt 加密后的密码',
    nickname VARCHAR(50) NOT NULL DEFAULT '' COMMENT '昵称',
    email VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    avatar VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0=禁用, 1=正常）',
    token_version BIGINT NOT NULL DEFAULT 0 COMMENT '令牌安全版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '角色编码',
    name VARCHAR(100) NOT NULL COMMENT '角色名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '角色说明',
    system_role TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置角色',
    super_admin TINYINT NOT NULL DEFAULT 0 COMMENT '是否超级管理员角色',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (code),
    KEY idx_sys_role_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='系统角色';

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(100) NOT NULL COMMENT '权限编码',
    name VARCHAR(100) NOT NULL COMMENT '权限名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '权限说明',
    system_permission TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置权限',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (code),
    KEY idx_sys_permission_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='系统权限';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_by BIGINT DEFAULT NULL COMMENT '分配人ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sys_user_role_role (role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id)
        REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户角色关联';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT COMMENT '主键',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id),
    KEY idx_sys_role_permission_permission (permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id)
        REFERENCES sys_permission(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='角色权限关联';

INSERT INTO sys_role
    (code, name, description, system_role, super_admin, status, deleted)
VALUES
    ('USER', '普通用户', '发布、查看、收藏和下载资源', 1, 0, 1, 0),
    ('ADMIN', '内容管理员', '用户管理和资源审核', 1, 0, 1, 0),
    ('SUPER_ADMIN', '超级管理员', '角色权限与平台全部管理能力', 1, 1, 1, 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    system_role = VALUES(system_role),
    super_admin = VALUES(super_admin),
    status = 1,
    deleted = 0;

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
    name = VALUES(name),
    description = VALUES(description),
    system_permission = VALUES(system_permission),
    status = 1,
    deleted = 0;

-- USER：日常资源权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'resource:read', 'resource:download', 'resource:favorite',
    'resource:create', 'resource:update', 'resource:delete'
)
WHERE r.code = 'USER';

-- ADMIN：包含用户管理和资源审核，但不能修改 RBAC
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code <> 'rbac:manage'
WHERE r.code = 'ADMIN';

-- SUPER_ADMIN：全部权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'SUPER_ADMIN';
