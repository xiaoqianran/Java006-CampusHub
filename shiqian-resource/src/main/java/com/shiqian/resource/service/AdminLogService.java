package com.shiqian.resource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.entity.AdminLog;

/**
 * 轻量级管理员操作审计日志服务（内存实现）
 */
public interface AdminLogService {

    /**
     * 记录一条操作日志
     */
    void recordLog(Long operatorId, String action, Long targetId, String detail);

    /**
     * 分页查询日志，支持按 action 过滤
     */
    Page<AdminLog> pageLogs(Integer page, Integer size, String action);
}
