USE `shiqian_user`;

DROP PROCEDURE IF EXISTS `upgrade_security_phase1`;
DELIMITER //
CREATE PROCEDURE `upgrade_security_phase1`()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'shiqian_user'
          AND TABLE_NAME = 'sys_user'
          AND COLUMN_NAME = 'token_version'
    ) THEN
        ALTER TABLE `sys_user`
            ADD COLUMN `token_version` BIGINT NOT NULL DEFAULT 0
                COMMENT '令牌安全版本' AFTER `status`;
    END IF;
END//
DELIMITER ;

CALL `upgrade_security_phase1`();
DROP PROCEDURE IF EXISTS `upgrade_security_phase1`;

USE `shiqian_resource`;

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
