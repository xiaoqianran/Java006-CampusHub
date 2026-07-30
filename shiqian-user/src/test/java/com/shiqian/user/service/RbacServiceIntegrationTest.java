package com.shiqian.user.service;

import com.shiqian.common.exception.BusinessException;
import com.shiqian.common.security.AuthoritySnapshot;
import com.shiqian.common.security.JwtUtil;
import com.shiqian.user.entity.User;
import com.shiqian.user.mapper.RbacQueryMapper;
import com.shiqian.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacServiceIntegrationTest {

    @Autowired
    private RbacService rbacService;

    @Autowired
    private RbacQueryMapper queryMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthorityCacheService authorityCacheService;

    @MockBean
    private TokenSessionService tokenSessionService;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("DELETE FROM sys_user_role");
        jdbcTemplate.execute("DELETE FROM sys_user");
        jdbcTemplate.execute(
                "DELETE FROM sys_role_permission WHERE role_id > 3 OR permission_id > 9");
        jdbcTemplate.execute("DELETE FROM sys_role WHERE id > 3");
        jdbcTemplate.execute("DELETE FROM sys_permission WHERE id > 9");
        when(authorityCacheService.get(anyLong())).thenReturn(Optional.empty());
        when(tokenSessionService.isCurrentAccessToken(any())).thenReturn(true);
    }

    @Test
    void shouldMergePermissionsFromMultipleRoles() {
        User user = insertUser("multi-role");
        assignRole(user.getId(), 1L);
        assignRole(user.getId(), 2L);

        AuthoritySnapshot snapshot = rbacService.getAuthoritySnapshot(user.getId());

        assertEquals(2, snapshot.getRoles().size());
        assertTrue(snapshot.getRoles().containsAll(List.of("USER", "ADMIN")));
        assertTrue(snapshot.getPermissions().contains("resource:create"));
        assertTrue(snapshot.getPermissions().contains("resource:audit"));
        assertTrue(snapshot.getPermissions().contains("user:manage"));
        assertTrue(!snapshot.getPermissions().contains("rbac:manage"));
    }

    @Test
    void replacingUserRolesShouldRevokeTokensAndEvictAuthorityCache() {
        User user = insertUser("role-change");
        assignRole(user.getId(), 1L);

        rbacService.replaceUserRolesByCodes(
                user.getId(),
                List.of("ADMIN"),
                99L);

        User updated = userMapper.selectById(user.getId());
        assertEquals(1L, updated.getTokenVersion());
        assertEquals(List.of("ADMIN"), queryMapper.selectRoleCodes(user.getId()));
        verify(tokenSessionService).syncUserVersion(user.getId(), 1L);
        verify(tokenSessionService).revokeAll(user.getId());
        verify(authorityCacheService).evictAll(List.of(user.getId()));
    }

    @Test
    void shouldProtectTheLastEnabledSuperAdministrator() {
        User superAdmin = insertUser("last-super");
        assignRole(superAdmin.getId(), 3L);

        BusinessException removeException = assertThrows(
                BusinessException.class,
                () -> rbacService.replaceUserRolesByCodes(
                        superAdmin.getId(),
                        List.of("USER"),
                        99L));
        BusinessException disableException = assertThrows(
                BusinessException.class,
                () -> rbacService.assertCanDisableUser(superAdmin.getId()));

        assertTrue(removeException.getMessage().contains("最后一个"));
        assertTrue(disableException.getMessage().contains("最后一个"));
        assertEquals(List.of("SUPER_ADMIN"),
                queryMapper.selectRoleCodes(superAdmin.getId()));
    }

    @Test
    void onlyRbacManagerCanCallRoleManagementEndpoint() throws Exception {
        User admin = insertUser("content-admin");
        assignRole(admin.getId(), 2L);
        User superAdmin = insertUser("super-admin");
        assignRole(superAdmin.getId(), 3L);

        String adminToken = jwtUtil.generateAccessToken(
                admin.getId(),
                admin.getUsername(),
                "ADMIN",
                admin.getTokenVersion());
        String superToken = jwtUtil.generateAccessToken(
                superAdmin.getId(),
                superAdmin.getUsername(),
                "ADMIN",
                superAdmin.getTokenVersion());

        mockMvc.perform(get("/api/user/admin/rbac/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/user/admin/rbac/roles")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    private User insertUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("encoded-password");
        user.setNickname(username);
        user.setStatus(1);
        user.setTokenVersion(0L);
        user.setDeleted(0);
        userMapper.insert(user);
        return user;
    }

    private void assignRole(Long userId, Long roleId) {
        jdbcTemplate.update(
                "INSERT INTO sys_user_role(user_id, role_id) VALUES (?, ?)",
                userId,
                roleId);
    }
}
