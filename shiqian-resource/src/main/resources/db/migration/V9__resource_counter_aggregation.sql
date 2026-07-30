CREATE TABLE IF NOT EXISTS t_counter_flush_batch (
  batch_id VARCHAR(36) NOT NULL PRIMARY KEY,
  counter_type VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_counter_batch_status_time (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Redis资源计数批量写回幂等记录';
