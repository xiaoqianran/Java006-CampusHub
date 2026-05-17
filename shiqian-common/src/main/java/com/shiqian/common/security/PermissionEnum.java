package com.shiqian.common.security;

import lombok.Getter;

/**
 * 权限枚举
 */
@Getter
public enum PermissionEnum {

    RESOURCE_READ("resource:read", "资源查看"),
    RESOURCE_DOWNLOAD("resource:download", "资源下载"),
    RESOURCE_FAVORITE("resource:favorite", "资源收藏"),
    RESOURCE_CREATE("resource:create", "资源创建"),
    RESOURCE_UPDATE("resource:update", "资源更新"),
    RESOURCE_DELETE("resource:delete", "资源删除"),
    RESOURCE_AUDIT("resource:audit", "资源审核"),
    USER_MANAGE("user:manage", "用户管理");

    private final String code;
    private final String description;

    PermissionEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
