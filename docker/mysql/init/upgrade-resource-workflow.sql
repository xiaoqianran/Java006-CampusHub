USE `shiqian_resource`;

DROP PROCEDURE IF EXISTS `upgrade_resource_workflow`;
DELIMITER //
CREATE PROCEDURE `upgrade_resource_workflow`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'shiqian_resource'
          AND TABLE_NAME = 't_resource'
          AND COLUMN_NAME = 'review_reason'
    ) THEN
        ALTER TABLE `t_resource`
            ADD COLUMN `review_reason` VARCHAR(500) DEFAULT NULL COMMENT '审核意见' AFTER `status`,
            ADD COLUMN `reviewer_id` BIGINT DEFAULT NULL COMMENT '审核人ID' AFTER `review_reason`,
            ADD COLUMN `review_time` DATETIME DEFAULT NULL COMMENT '最近审核时间' AFTER `reviewer_id`,
            ADD COLUMN `offline_reason` VARCHAR(500) DEFAULT NULL COMMENT '下架原因' AFTER `review_time`,
            ADD COLUMN `published_time` DATETIME DEFAULT NULL COMMENT '发布时间' AFTER `offline_reason`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'shiqian_resource'
          AND TABLE_NAME = 't_resource'
          AND COLUMN_NAME = 'content_scene'
    ) THEN
        ALTER TABLE `t_resource`
            ADD COLUMN `content_scene` VARCHAR(30) NOT NULL DEFAULT 'SHARE'
                COMMENT '内容频道 BLOG/GALLERY/SHARE' AFTER `content_type`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'shiqian_resource'
          AND TABLE_NAME = 't_resource'
          AND COLUMN_NAME = 'tags'
    ) THEN
        ALTER TABLE `t_resource`
            ADD COLUMN `tags` VARCHAR(500) DEFAULT NULL
                COMMENT '可选自由标签，逗号分隔' AFTER `content_scene`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = 'shiqian_resource'
          AND TABLE_NAME = 't_resource'
          AND INDEX_NAME = 'idx_content_scene'
    ) THEN
        ALTER TABLE `t_resource`
            ADD INDEX `idx_content_scene` (`content_scene`);
    END IF;

    UPDATE `t_resource`
    SET `content_scene` = 'SHARE'
    WHERE `content_scene` IS NULL OR `content_scene` = '';
END//
DELIMITER ;

CALL `upgrade_resource_workflow`();
DROP PROCEDURE IF EXISTS `upgrade_resource_workflow`;
