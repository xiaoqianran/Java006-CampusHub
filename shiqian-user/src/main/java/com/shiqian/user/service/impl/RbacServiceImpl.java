package com.shiqian.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.common.security.AuthoritySnapshot;
import com.shiqian.user.dto.PermissionCreateDTO;
import com.shiqian.user.dto.PermissionUpdateDTO;
import com.shiqian.user.dto.PermissionVO;
import com.shiqian.user.dto.RoleCreateDTO;
import com.shiqian.user.dto.RolePermissionCodeRow;
import com.shiqian.user.dto.RoleUpdateDTO;
import com.shiqian.user.dto.RoleVO;
import com.shiqian.user.dto.UserRoleCodeRow;
import com.shiqian.user.entity.SysPermission;
import com.shiqian.user.entity.SysRole;
import com.shiqian.user.entity.SysRolePermission;
import com.shiqian.user.entity.SysUserRole;
import com.shiqian.user.entity.User;
import com.shiqian.user.mapper.RbacQueryMapper;
import com.shiqian.user.mapper.SysPermissionMapper;
import com.shiqian.user.mapper.SysRoleMapper;
import com.shiqian.user.mapper.SysRolePermissionMapper;
import com.shiqian.user.mapper.SysUserRoleMapper;
import com.shiqian.user.mapper.UserMapper;
import com.shiqian.user.service.AuthorityCacheService;
import com.shiqian.user.service.RbacService;
import com.shiqian.user.service.TokenSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RbacServiceImpl implements RbacService {

    private static final String USER_ROLE = "USER";
    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
    private static final String RBAC_MANAGE_PERMISSION = "rbac:manage";

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final RbacQueryMapper queryMapper;
    private final UserMapper userMapper;
    private final AuthorityCacheService authorityCacheService;
    private final TokenSessionService tokenSessionService;

    @Override
    public AuthoritySnapshot getAuthoritySnapshot(Long userId) {
        if (userId == null) {
            return new AuthoritySnapshot(Set.of(), Set.of());
        }
        return authorityCacheService.get(userId).orElseGet(() -> {
            AuthoritySnapshot snapshot = AuthoritySnapshot.fromGrantedAuthorities(
                    queryMapper.selectGrantedAuthorities(userId));
            authorityCacheService.put(userId, snapshot);
            return snapshot;
        });
    }

    @Override
    public Map<Long, Set<String>> getRoleCodesByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> uniqueIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<String>> result = new LinkedHashMap<>();
        queryMapper.selectRoleCodesByUserIds(uniqueIds).forEach(row ->
                result.computeIfAbsent(row.getUserId(), ignored -> new LinkedHashSet<>())
                        .add(row.getRoleCode()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignDefaultRole(Long userId) {
        SysRole role = findRoleByCode(USER_ROLE);
        insertUserRole(userId, role.getId(), null);
        evictAfterCommit(List.of(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceUserRolesByCodes(
            Long targetUserId,
            List<String> roleCodes,
            Long operatorId) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BusinessException("至少分配一个角色");
        }
        Set<String> normalized = roleCodes.stream()
                .filter(Objects::nonNull)
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .filter(code -> !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            throw new BusinessException("至少分配一个有效角色");
        }
        List<SysRole> roles = roleMapper.selectList(
                new QueryWrapper<SysRole>()
                        .in("code", normalized)
                        .eq("status", 1)
                        .eq("deleted", 0));
        if (roles.size() != normalized.size()) {
            throw new BusinessException("角色不存在或已禁用");
        }
        replaceUserRoles(targetUserId, roles, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceUserRolesByIds(
            Long targetUserId,
            List<Long> roleIds,
            Long operatorId) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException("至少分配一个角色");
        }
        Set<Long> uniqueRoleIds = roleIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueRoleIds.isEmpty()) {
            throw new BusinessException("至少分配一个有效角色");
        }
        List<SysRole> roles = roleMapper.selectBatchIds(uniqueRoleIds).stream()
                .filter(role -> role.getDeleted() == null || role.getDeleted() == 0)
                .filter(role -> role.getStatus() != null && role.getStatus() == 1)
                .toList();
        if (roles.size() != uniqueRoleIds.size()) {
            throw new BusinessException("角色不存在或已禁用");
        }
        replaceUserRoles(targetUserId, roles, operatorId);
    }

    private void replaceUserRoles(
            Long targetUserId,
            List<SysRole> roles,
            Long operatorId) {
        User user = requireUser(targetUserId);
        boolean currentlySuperAdmin =
                queryMapper.countSuperAdminRoleForUser(targetUserId) > 0;
        boolean remainsSuperAdmin = roles.stream()
                .anyMatch(role -> SUPER_ADMIN_ROLE.equals(role.getCode()));
        if (currentlySuperAdmin
                && !remainsSuperAdmin
                && user.getStatus() != null
                && user.getStatus() == 1
                && queryMapper.countEnabledSuperAdmins() <= 1) {
            throw new BusinessException("不能移除最后一个启用超级管理员的角色");
        }

        userRoleMapper.delete(
                new QueryWrapper<SysUserRole>().eq("user_id", targetUserId));
        roles.forEach(role -> insertUserRole(targetUserId, role.getId(), operatorId));
        invalidateUserTokens(user);
        evictAfterCommit(List.of(targetUserId));
    }

    @Override
    public void assertCanDisableUser(Long userId) {
        if (queryMapper.countSuperAdminRoleForUser(userId) > 0
                && queryMapper.countEnabledSuperAdmins() <= 1) {
            throw new BusinessException("不能禁用最后一个启用的超级管理员");
        }
    }

    @Override
    public List<RoleVO> listRoles() {
        Map<Long, Set<String>> permissionsByRole = new LinkedHashMap<>();
        for (RolePermissionCodeRow row : queryMapper.selectAllRolePermissionCodes()) {
            permissionsByRole
                    .computeIfAbsent(row.getRoleId(), ignored -> new LinkedHashSet<>())
                    .add(row.getPermissionCode());
        }
        return roleMapper.selectList(
                        new QueryWrapper<SysRole>()
                                .eq("deleted", 0)
                                .orderByDesc("super_admin")
                                .orderByAsc("id"))
                .stream()
                .map(role -> toRoleVO(
                        role,
                        permissionsByRole.getOrDefault(role.getId(), Set.of())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO createRole(RoleCreateDTO dto) {
        String code = dto.getCode().trim().toUpperCase(Locale.ROOT);
        if (roleMapper.selectCount(
                new QueryWrapper<SysRole>().eq("code", code)) > 0) {
            throw new BusinessException(409, "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(dto.getName().trim());
        role.setDescription(dto.getDescription());
        role.setSystemRole(0);
        role.setSuperAdmin(0);
        role.setStatus(1);
        role.setDeleted(0);
        roleMapper.insert(role);
        return toRoleVO(role, Set.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long roleId, RoleUpdateDTO dto) {
        SysRole role = requireRole(roleId);
        int status = normalizeStatus(dto.getStatus(), role.getStatus());
        if (isSuperAdminRole(role) && status == 0) {
            throw new BusinessException("超级管理员角色不能禁用");
        }
        List<Long> affectedUsers = queryMapper.selectUserIdsByRoleId(roleId);
        role.setName(dto.getName().trim());
        role.setDescription(dto.getDescription());
        role.setStatus(status);
        roleMapper.updateById(role);
        evictAfterCommit(affectedUsers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId, Long operatorId) {
        SysRole role = requireRole(roleId);
        if (role.getSystemRole() != null && role.getSystemRole() == 1) {
            throw new BusinessException("内置角色不能删除");
        }
        List<Long> affectedUsers = queryMapper.selectUserIdsByRoleId(roleId);
        userRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("role_id", roleId));
        rolePermissionMapper.delete(
                new QueryWrapper<SysRolePermission>().eq("role_id", roleId));
        roleMapper.deleteById(roleId);

        SysRole defaultRole = findRoleByCode(USER_ROLE);
        for (Long userId : affectedUsers) {
            if (queryMapper.selectRoleCodes(userId).isEmpty()) {
                insertUserRole(userId, defaultRole.getId(), operatorId);
            }
            User user = userMapper.selectById(userId);
            if (user != null && (user.getDeleted() == null || user.getDeleted() == 0)) {
                invalidateUserTokens(user);
            }
        }
        evictAfterCommit(affectedUsers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceRolePermissions(Long roleId, List<Long> permissionIds) {
        SysRole role = requireRole(roleId);
        if (isSuperAdminRole(role)) {
            throw new BusinessException("超级管理员权限由系统维护，不能手动删减");
        }
        Set<Long> uniqueIds = permissionIds == null
                ? Set.of()
                : permissionIds.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        List<SysPermission> permissions = uniqueIds.isEmpty()
                ? List.of()
                : permissionMapper.selectBatchIds(uniqueIds).stream()
                        .filter(permission -> permission.getDeleted() == null
                                || permission.getDeleted() == 0)
                        .filter(permission -> permission.getStatus() != null
                                && permission.getStatus() == 1)
                        .toList();
        if (permissions.size() != uniqueIds.size()) {
            throw new BusinessException("权限不存在或已禁用");
        }

        List<Long> affectedUsers = queryMapper.selectUserIdsByRoleId(roleId);
        rolePermissionMapper.delete(
                new QueryWrapper<SysRolePermission>().eq("role_id", roleId));
        permissions.forEach(permission ->
                insertRolePermission(roleId, permission.getId()));
        evictAfterCommit(affectedUsers);
    }

    @Override
    public List<PermissionVO> listPermissions() {
        return permissionMapper.selectList(
                        new QueryWrapper<SysPermission>()
                                .eq("deleted", 0)
                                .orderByAsc("id"))
                .stream()
                .map(this::toPermissionVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionVO createPermission(PermissionCreateDTO dto) {
        String code = dto.getCode().trim().toLowerCase(Locale.ROOT);
        if (permissionMapper.selectCount(
                new QueryWrapper<SysPermission>().eq("code", code)) > 0) {
            throw new BusinessException(409, "权限编码已存在");
        }
        SysPermission permission = new SysPermission();
        permission.setCode(code);
        permission.setName(dto.getName().trim());
        permission.setDescription(dto.getDescription());
        permission.setSystemPermission(0);
        permission.setStatus(1);
        permission.setDeleted(0);
        permissionMapper.insert(permission);

        SysRole superAdmin = findRoleByCode(SUPER_ADMIN_ROLE);
        insertRolePermission(superAdmin.getId(), permission.getId());
        evictAfterCommit(queryMapper.selectUserIdsByRoleId(superAdmin.getId()));
        return toPermissionVO(permission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePermission(Long permissionId, PermissionUpdateDTO dto) {
        SysPermission permission = requirePermission(permissionId);
        int status = normalizeStatus(dto.getStatus(), permission.getStatus());
        if (permission.getSystemPermission() != null
                && permission.getSystemPermission() == 1
                && status == 0) {
            throw new BusinessException("内置权限不能禁用");
        }
        List<Long> affectedUsers = userIdsWithPermission(permissionId);
        permission.setName(dto.getName().trim());
        permission.setDescription(dto.getDescription());
        permission.setStatus(status);
        permissionMapper.updateById(permission);
        evictAfterCommit(affectedUsers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePermission(Long permissionId) {
        SysPermission permission = requirePermission(permissionId);
        if (permission.getSystemPermission() != null
                && permission.getSystemPermission() == 1) {
            throw new BusinessException("内置权限不能删除");
        }
        List<Long> affectedUsers = userIdsWithPermission(permissionId);
        rolePermissionMapper.delete(
                new QueryWrapper<SysRolePermission>()
                        .eq("permission_id", permissionId));
        permissionMapper.deleteById(permissionId);
        evictAfterCommit(affectedUsers);
    }

    private List<Long> userIdsWithPermission(Long permissionId) {
        List<Long> roleIds = rolePermissionMapper.selectList(
                        new QueryWrapper<SysRolePermission>()
                                .eq("permission_id", permissionId))
                .stream()
                .map(SysRolePermission::getRoleId)
                .distinct()
                .toList();
        return roleIds.stream()
                .flatMap(roleId -> queryMapper.selectUserIdsByRoleId(roleId).stream())
                .distinct()
                .toList();
    }

    private void insertUserRole(Long userId, Long roleId, Long operatorId) {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        relation.setCreatedBy(operatorId);
        relation.setCreateTime(LocalDateTime.now());
        userRoleMapper.insert(relation);
    }

    private void insertRolePermission(Long roleId, Long permissionId) {
        SysRolePermission relation = new SysRolePermission();
        relation.setRoleId(roleId);
        relation.setPermissionId(permissionId);
        relation.setCreateTime(LocalDateTime.now());
        rolePermissionMapper.insert(relation);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    private SysRole requireRole(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null || (role.getDeleted() != null && role.getDeleted() == 1)) {
            throw new BusinessException(404, "角色不存在");
        }
        return role;
    }

    private SysPermission requirePermission(Long permissionId) {
        SysPermission permission = permissionMapper.selectById(permissionId);
        if (permission == null
                || (permission.getDeleted() != null && permission.getDeleted() == 1)) {
            throw new BusinessException(404, "权限不存在");
        }
        return permission;
    }

    private SysRole findRoleByCode(String code) {
        SysRole role = roleMapper.selectOne(
                new QueryWrapper<SysRole>()
                        .eq("code", code)
                        .eq("deleted", 0));
        if (role == null || role.getStatus() == null || role.getStatus() != 1) {
            throw new BusinessException(500, "系统角色配置缺失: " + code);
        }
        return role;
    }

    private int normalizeStatus(Integer requested, Integer current) {
        int value = requested != null ? requested : (current != null ? current : 1);
        if (value != 0 && value != 1) {
            throw new BusinessException("状态只能是0或1");
        }
        return value;
    }

    private boolean isSuperAdminRole(SysRole role) {
        return SUPER_ADMIN_ROLE.equals(role.getCode())
                || (role.getSuperAdmin() != null && role.getSuperAdmin() == 1);
    }

    private void invalidateUserTokens(User user) {
        long nextVersion = (user.getTokenVersion() != null
                ? user.getTokenVersion()
                : 0L) + 1;
        user.setTokenVersion(nextVersion);
        tokenSessionService.syncUserVersion(user.getId(), nextVersion);
        tokenSessionService.revokeAll(user.getId());
        userMapper.updateById(user);
    }

    private void evictAfterCommit(Collection<Long> userIds) {
        List<Long> ids = userIds == null
                ? List.of()
                : userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            authorityCacheService.evictAll(ids);
                        }
                    });
        } else {
            authorityCacheService.evictAll(ids);
        }
    }

    private RoleVO toRoleVO(SysRole role, Set<String> permissionCodes) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        vo.setDescription(role.getDescription());
        vo.setSystemRole(role.getSystemRole() != null && role.getSystemRole() == 1);
        vo.setSuperAdmin(role.getSuperAdmin() != null && role.getSuperAdmin() == 1);
        vo.setStatus(role.getStatus());
        vo.setPermissionCodes(new LinkedHashSet<>(permissionCodes));
        return vo;
    }

    private PermissionVO toPermissionVO(SysPermission permission) {
        PermissionVO vo = new PermissionVO();
        vo.setId(permission.getId());
        vo.setCode(permission.getCode());
        vo.setName(permission.getName());
        vo.setDescription(permission.getDescription());
        vo.setSystemPermission(permission.getSystemPermission() != null
                && permission.getSystemPermission() == 1);
        vo.setStatus(permission.getStatus());
        return vo;
    }
}
