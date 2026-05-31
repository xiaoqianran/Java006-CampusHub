package com.shiqian.resource.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轻量级管理员操作日志 POJO（内存存储，无数据库表）
 */
@Data
public class AdminLog {
    private Long id;
    private Long operatorId;
    private String action;      // e.g. RESOURCE_AUDIT, USER_STATUS_CHANGE, RESOURCE_RESTORE, RESOURCE_PERMANENT_DELETE
    private Long targetId;
    private String detail;
    private LocalDateTime createTime;
}
