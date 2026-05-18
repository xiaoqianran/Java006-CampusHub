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
    `password` VARCHAR(200) NOT NULL COMMENT 'BCrypt加密后的密码',
    `nickname` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '昵称',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色（USER/ADMIN）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0=禁用, 1=正常）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=正常, 1=已删除）',

    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_username` (`username`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户表';

INSERT INTO `t_user` (`id`, `username`, `password`, `nickname`, `email`, `phone`, `role`, `status`, `deleted`)
VALUES
    (1, 'admin', '$2a$10$oAFwmKHtlJ/E4CZPrRJqFe/m94IQs5ZDN.VaQR4HYfB6EeoJS/HCS', '管理员', 'admin@example.com', '13800138000', 'ADMIN', 1, 0),
    (2, 'student01', '$2a$10$oAFwmKHtlJ/E4CZPrRJqFe/m94IQs5ZDN.VaQR4HYfB6EeoJS/HCS', '学生一号', 'student01@example.com', '13800138001', 'USER', 1, 0)
ON DUPLICATE KEY UPDATE
    `password` = VALUES(`password`),
    `nickname` = VALUES(`nickname`),
    `email` = VALUES(`email`),
    `phone` = VALUES(`phone`),
    `role` = VALUES(`role`),
    `status` = VALUES(`status`),
    `deleted` = 0;

USE `shiqian_resource`;

CREATE TABLE IF NOT EXISTS `t_category` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父分类ID，0为根分类',
    `name` VARCHAR(100) NOT NULL COMMENT '分类名称',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `icon` VARCHAR(255) DEFAULT NULL COMMENT '图标URL',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1=启用, 0=禁用）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=正常, 1=已删除）',

    PRIMARY KEY (`id`),
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='资源分类表';

CREATE TABLE IF NOT EXISTS `t_favorite` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `resource_id` BIGINT NOT NULL COMMENT '资源ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_resource` (`user_id`, `resource_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_resource_id` (`resource_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='资源收藏表';

INSERT INTO `t_category` (`id`, `parent_id`, `name`, `sort_order`, `icon`, `status`, `deleted`)
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
    `parent_id` = VALUES(`parent_id`),
    `name` = VALUES(`name`),
    `sort_order` = VALUES(`sort_order`),
    `icon` = VALUES(`icon`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `t_resource` (
    `id`,
    `user_id`,
    `title`,
    `description`,
    `summary`,
    `content_markdown`,
    `content_type`,
    `category_id`,
    `file_url`,
    `file_size`,
    `file_type`,
    `download_count`,
    `version`,
    `status`,
    `deleted`
)
VALUES
    (
        1,
        2,
        '计算机网络实验三：路由协议配置',
        '包含 RIP、OSPF 路由配置步骤、拓扑图与常见错误排查。',
        '计算机网络路由协议实验资料',
        NULL,
        'FILE',
        1,
        'https://example.com/resources/network-lab-3.pdf',
        2456789,
        '实验报告',
        426,
        1,
        1,
        0
    ),
    (
        2,
        2,
        '数据结构期末复习提纲与真题解析',
        '覆盖线性表、树、图、排序、查找等核心知识点。',
        '数据结构期末复习资料',
        NULL,
        'FILE',
        1,
        'https://example.com/resources/data-structure-review.pdf',
        1945600,
        '复习资料',
        338,
        1,
        0,
        0
    ),
    (
        3,
        2,
        '高等数学上册重点公式速查表',
        '极限、导数、积分、级数常见公式与题型归纳。',
        '高等数学公式速查资料',
        NULL,
        'FILE',
        2,
        'https://example.com/resources/math-formula.pdf',
        884736,
        '公式整理',
        880,
        1,
        1,
        0
    ),
    (
        4,
        2,
        '大学英语四级高频词汇与作文模板',
        '词汇分组、作文句型、听力训练方法。',
        '大学英语四级备考资料',
        NULL,
        'FILE',
        3,
        'https://example.com/resources/cet4-template.pdf',
        1024000,
        '考试资料',
        220,
        1,
        2,
        0
    ),
    (
        5,
        2,
        'Java Spring Boot 项目脚手架说明',
        '适合课程设计使用的后端项目结构、常用依赖与接口示例。',
        'Spring Boot 课程设计项目脚手架说明',
        NULL,
        'FILE',
        1,
        'https://example.com/resources/spring-boot-starter.zip',
        4096000,
        '项目模板',
        502,
        1,
        1,
        0
    ),
    (
        6,
        1,
        '考研数学一真题分类精讲',
        '按知识点拆分近年真题，并配有解题思路。',
        '考研数学一真题分类解析资料',
        NULL,
        'FILE',
        4,
        'https://example.com/resources/postgraduate-math.pdf',
        3072000,
        '真题解析',
        420,
        1,
        1,
        0
    )
ON DUPLICATE KEY UPDATE
    `user_id` = VALUES(`user_id`),
    `title` = VALUES(`title`),
    `description` = VALUES(`description`),
    `summary` = VALUES(`summary`),
    `content_markdown` = VALUES(`content_markdown`),
    `content_type` = VALUES(`content_type`),
    `category_id` = VALUES(`category_id`),
    `file_url` = VALUES(`file_url`),
    `file_size` = VALUES(`file_size`),
    `file_type` = VALUES(`file_type`),
    `download_count` = VALUES(`download_count`),
    `version` = VALUES(`version`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT IGNORE INTO `t_favorite` (`user_id`, `resource_id`)
VALUES
    (2, 1),
    (2, 3),
    (2, 5);