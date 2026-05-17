package com.shiqian.common.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 角色权限映射
 */
public class RolePermissionMapping {

    private static final Map<RoleEnum, List<PermissionEnum>> MAPPING = Map.of(
            RoleEnum.USER, Arrays.asList(
                    PermissionEnum.RESOURCE_READ,
                    PermissionEnum.RESOURCE_DOWNLOAD,
                    PermissionEnum.RESOURCE_FAVORITE,
                    PermissionEnum.RESOURCE_CREATE,
                    PermissionEnum.RESOURCE_UPDATE,
                    PermissionEnum.RESOURCE_DELETE
            ),
            RoleEnum.ADMIN, Arrays.asList(
                    PermissionEnum.RESOURCE_READ,
                    PermissionEnum.RESOURCE_DOWNLOAD,
                    PermissionEnum.RESOURCE_FAVORITE,
                    PermissionEnum.RESOURCE_CREATE,
                    PermissionEnum.RESOURCE_UPDATE,
                    PermissionEnum.RESOURCE_DELETE,
                    PermissionEnum.RESOURCE_AUDIT,
                    PermissionEnum.USER_MANAGE
            )
    );

    public static List<PermissionEnum> getPermissions(RoleEnum role) {
        return MAPPING.getOrDefault(role, Collections.emptyList());
    }

    public static List<PermissionEnum> getPermissions(String roleName) {
        try {
            return getPermissions(RoleEnum.valueOf(roleName));
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }
    }
}
