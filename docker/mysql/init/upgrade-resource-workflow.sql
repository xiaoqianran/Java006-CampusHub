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
END//
DELIMITER ;

CALL `upgrade_resource_workflow`();
DROP PROCEDURE IF EXISTS `upgrade_resource_workflow`;
