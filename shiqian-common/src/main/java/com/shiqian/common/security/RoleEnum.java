package com.shiqian.common.security;

import lombok.Getter;

/**
 * 角色枚举
 */
@Getter
public enum RoleEnum {

    USER("普通用户"),
    ADMIN("管理员");

    private final String description;

    RoleEnum(String description) {
        this.description = description;
    }
}
