package com.shiqian.user.controller;

import com.shiqian.common.result.Result;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.user.dto.AssignPermissionsDTO;
import com.shiqian.user.dto.AssignRolesDTO;
import com.shiqian.user.dto.PermissionCreateDTO;
import com.shiqian.user.dto.PermissionUpdateDTO;
import com.shiqian.user.dto.PermissionVO;
import com.shiqian.user.dto.RoleCreateDTO;
import com.shiqian.user.dto.RoleUpdateDTO;
import com.shiqian.user.dto.RoleVO;
import com.shiqian.user.service.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "角色权限管理", description = "数据库驱动的角色、权限和用户多角色管理")
@Validated
@RestController
@RequestMapping("/api/user/admin/rbac")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('rbac:manage')")
public class RbacAdminController {

    private final RbacService rbacService;

    @Operation(summary = "查询角色及其权限")
    @GetMapping("/roles")
    public Result<List<RoleVO>> listRoles() {
        return Result.ok(rbacService.listRoles());
    }

    @Operation(summary = "创建角色")
    @PostMapping("/roles")
    public Result<RoleVO> createRole(@RequestBody @Valid RoleCreateDTO dto) {
        return Result.ok(rbacService.createRole(dto));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/roles/{roleId}")
    public Result<Void> updateRole(
            @PathVariable @Positive Long roleId,
            @RequestBody @Valid RoleUpdateDTO dto) {
        rbacService.updateRole(roleId, dto);
        return Result.ok();
    }

    @Operation(summary = "删除自定义角色")
    @DeleteMapping("/roles/{roleId}")
    public Result<Void> deleteRole(@PathVariable @Positive Long roleId) {
        rbacService.deleteRole(roleId, SecurityUtil.getCurrentUserId());
        return Result.ok();
    }

    @Operation(summary = "替换角色权限")
    @PutMapping("/roles/{roleId}/permissions")
    public Result<Void> replaceRolePermissions(
            @PathVariable @Positive Long roleId,
            @RequestBody @Valid AssignPermissionsDTO dto) {
        rbacService.replaceRolePermissions(roleId, dto.getPermissionIds());
        return Result.ok();
    }

    @Operation(summary = "查询权限")
    @GetMapping("/permissions")
    public Result<List<PermissionVO>> listPermissions() {
        return Result.ok(rbacService.listPermissions());
    }

    @Operation(summary = "创建权限")
    @PostMapping("/permissions")
    public Result<PermissionVO> createPermission(
            @RequestBody @Valid PermissionCreateDTO dto) {
        return Result.ok(rbacService.createPermission(dto));
    }

    @Operation(summary = "更新权限")
    @PutMapping("/permissions/{permissionId}")
    public Result<Void> updatePermission(
            @PathVariable @Positive Long permissionId,
            @RequestBody @Valid PermissionUpdateDTO dto) {
        rbacService.updatePermission(permissionId, dto);
        return Result.ok();
    }

    @Operation(summary = "删除自定义权限")
    @DeleteMapping("/permissions/{permissionId}")
    public Result<Void> deletePermission(
            @PathVariable @Positive Long permissionId) {
        rbacService.deletePermission(permissionId);
        return Result.ok();
    }

    @Operation(summary = "替换用户的多个角色")
    @PutMapping("/users/{userId}/roles")
    public Result<Void> replaceUserRoles(
            @PathVariable @Positive Long userId,
            @RequestBody @Valid AssignRolesDTO dto) {
        rbacService.replaceUserRolesByIds(
                userId,
                dto.getRoleIds(),
                SecurityUtil.getCurrentUserId());
        return Result.ok();
    }
}
