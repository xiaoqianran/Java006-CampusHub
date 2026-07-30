CREATE TABLE IF NOT EXISTS t_sensitive_word (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word VARCHAR(100) NOT NULL,
    level TINYINT NOT NULL DEFAULT 2,
    status TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sensitive_word (word),
    INDEX idx_sensitive_word_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='可热更新敏感词';

CREATE TABLE IF NOT EXISTS t_content_review_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id BIGINT DEFAULT NULL,
    submitter_id BIGINT DEFAULT NULL,
    reviewer_id BIGINT DEFAULT NULL,
    review_type VARCHAR(20) NOT NULL,
    decision VARCHAR(30) NOT NULL,
    matched_words VARCHAR(1000) DEFAULT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    content_title VARCHAR(200) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_review_resource (resource_id, create_time),
    INDEX idx_review_type_decision (review_type, decision, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='自动与人工内容审核记录';
