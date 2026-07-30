package com.shiqian.resource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.entity.AdminLog;
import java.time.LocalDateTime;

public interface AdminLogService {

    /**
     * 记录一条操作日志
     */
    void recordLog(Long operatorId, String action, Long targetId, String detail);

    Page<AdminLog> pageLogs(
            Integer page,
            Integer size,
            String action,
            Long operatorId,
            LocalDateTime startTime,
            LocalDateTime endTime);
}
