package com.shiqian.common.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 角色权限映射测试
 */
class RolePermissionMappingTest {

    @Test
    void shouldReturnUserPermissions() {
        List<PermissionEnum> permissions = RolePermissionMapping.getPermissions(RoleEnum.USER);
        assertFalse(permissions.isEmpty());
        assertTrue(permissions.contains(PermissionEnum.RESOURCE_READ));
        assertTrue(permissions.contains(PermissionEnum.RESOURCE_CREATE));
        assertFalse(permissions.contains(PermissionEnum.RESOURCE_AUDIT));
        assertFalse(permissions.contains(PermissionEnum.USER_MANAGE));
    }

    @Test
    void shouldReturnAdminPermissions() {
        List<PermissionEnum> permissions = RolePermissionMapping.getPermissions(RoleEnum.ADMIN);
        assertFalse(permissions.isEmpty());
        assertTrue(permissions.contains(PermissionEnum.RESOURCE_READ));
        assertTrue(permissions.contains(PermissionEnum.RESOURCE_AUDIT));
        assertTrue(permissions.contains(PermissionEnum.USER_MANAGE));
    }

    @Test
    void shouldReturnPermissionsByRoleName() {
        List<PermissionEnum> permissions = RolePermissionMapping.getPermissions("USER");
        assertFalse(permissions.isEmpty());
    }

    @Test
    void shouldReturnEmptyForUnknownRole() {
        List<PermissionEnum> permissions = RolePermissionMapping.getPermissions("UNKNOWN");
        assertTrue(permissions.isEmpty());
    }
}
