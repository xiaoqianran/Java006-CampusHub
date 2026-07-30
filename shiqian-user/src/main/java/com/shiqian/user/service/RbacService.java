package com.shiqian.user.service;

import com.shiqian.common.security.AuthoritySnapshot;
import com.shiqian.user.dto.PermissionCreateDTO;
import com.shiqian.user.dto.PermissionUpdateDTO;
import com.shiqian.user.dto.PermissionVO;
import com.shiqian.user.dto.RoleCreateDTO;
import com.shiqian.user.dto.RoleUpdateDTO;
import com.shiqian.user.dto.RoleVO;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RbacService {

    AuthoritySnapshot getAuthoritySnapshot(Long userId);

    Map<Long, Set<String>> getRoleCodesByUserIds(List<Long> userIds);

    void assignDefaultRole(Long userId);

    void replaceUserRolesByCodes(
            Long targetUserId,
            List<String> roleCodes,
            Long operatorId);

    void replaceUserRolesByIds(
            Long targetUserId,
            List<Long> roleIds,
            Long operatorId);

    void assertCanDisableUser(Long userId);

    List<RoleVO> listRoles();

    RoleVO createRole(RoleCreateDTO dto);

    void updateRole(Long roleId, RoleUpdateDTO dto);

    void deleteRole(Long roleId, Long operatorId);

    void replaceRolePermissions(Long roleId, List<Long> permissionIds);

    List<PermissionVO> listPermissions();

    PermissionVO createPermission(PermissionCreateDTO dto);

    void updatePermission(Long permissionId, PermissionUpdateDTO dto);

    void deletePermission(Long permissionId);
}
