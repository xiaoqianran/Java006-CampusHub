package com.shiqian.user.mapper;

import com.shiqian.user.dto.RolePermissionCodeRow;
import com.shiqian.user.dto.UserRoleCodeRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

@Mapper
public interface RbacQueryMapper {

    @Select("""
            SELECT CONCAT('ROLE_', r.code)
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.deleted = 0
            UNION
            SELECT p.code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            JOIN sys_role_permission rp ON rp.role_id = r.id
            JOIN sys_permission p ON p.id = rp.permission_id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.deleted = 0
              AND p.status = 1
              AND p.deleted = 0
            """)
    Set<String> selectGrantedAuthorities(@Param("userId") Long userId);

    @Select("""
            SELECT r.code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.deleted = 0
            ORDER BY r.super_admin DESC, r.id ASC
            """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT ur.user_id AS userId, r.code AS roleCode
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id IN
            <foreach collection="userIds" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
              AND r.status = 1
              AND r.deleted = 0
            ORDER BY r.super_admin DESC, r.id ASC
            </script>
            """)
    List<UserRoleCodeRow> selectRoleCodesByUserIds(
            @Param("userIds") List<Long> userIds);

    @Select("""
            SELECT ur.user_id
            FROM sys_user_role ur
            JOIN sys_user u ON u.id = ur.user_id
            WHERE ur.role_id = #{roleId}
              AND u.deleted = 0
            """)
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

    @Select("""
            SELECT COUNT(DISTINCT u.id)
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE r.code = 'SUPER_ADMIN'
              AND r.status = 1
              AND r.deleted = 0
              AND u.status = 1
              AND u.deleted = 0
            """)
    long countEnabledSuperAdmins();

    @Select("""
            SELECT COUNT(*)
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.code = 'SUPER_ADMIN'
              AND r.status = 1
              AND r.deleted = 0
            """)
    long countSuperAdminRoleForUser(@Param("userId") Long userId);

    @Select("""
            SELECT rp.role_id AS roleId, p.code AS permissionCode
            FROM sys_role_permission rp
            JOIN sys_permission p ON p.id = rp.permission_id
            WHERE p.deleted = 0
            ORDER BY rp.role_id, p.id
            """)
    List<RolePermissionCodeRow> selectAllRolePermissionCodes();
}
