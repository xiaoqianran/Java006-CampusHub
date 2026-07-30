package com.shiqian.user.service;

import com.shiqian.common.exception.BusinessException;
import com.shiqian.common.security.JwtUtil;
import com.shiqian.common.security.AuthoritySnapshot;
import com.shiqian.common.security.TokenType;
import com.shiqian.user.dto.ChangePasswordDTO;
import com.shiqian.user.dto.LoginDTO;
import com.shiqian.user.dto.LoginVO;
import com.shiqian.user.dto.RegisterDTO;
import com.shiqian.user.entity.User;
import com.shiqian.user.mapper.UserMapper;
import com.shiqian.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceSecurityTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenSessionService tokenSessionService;
    @Mock
    private RbacService rbacService;

    private JwtUtil jwtUtil;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "test-only-jwt-key-with-at-least-thirty-two-bytes");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 7_200_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", 604_800_000L);
        userService = new UserServiceImpl(
                userMapper,
                passwordEncoder,
                jwtUtil,
                tokenSessionService,
                rbacService);
    }

    @Test
    void refreshMustUseLatestDatabaseRoleAndRotateRefreshToken() {
        String oldRefresh = jwtUtil.generateRefreshToken(2L, "alice", "ADMIN", 4L);
        User current = user(2L, "alice", "USER", 4L);
        when(userMapper.selectById(2L)).thenReturn(current);
        when(rbacService.getAuthoritySnapshot(2L))
                .thenReturn(new AuthoritySnapshot(
                        Set.of("USER"),
                        Set.of("resource:read")));

        LoginVO result = userService.refresh(oldRefresh);

        assertEquals("USER", result.getRole());
        assertEquals("USER", jwtUtil.getRole(result.getAccessToken()));
        assertEquals(TokenType.ACCESS.name(), jwtUtil.getTokenType(result.getAccessToken()));
        assertEquals(TokenType.REFRESH.name(), jwtUtil.getTokenType(result.getRefreshToken()));
        verify(tokenSessionService).consumeRefreshToken(
                oldRefresh, 2L, jwtUtil.getJti(oldRefresh));
        verify(tokenSessionService).storeRefreshToken(
                result.getRefreshToken(), 2L, 4L);
    }

    @Test
    void registrationShouldPersistUserAndAssignDefaultDatabaseRole() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("new-user");
        dto.setPassword("plain-password");
        dto.setNickname("新用户");
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(12L);
            return 1;
        });

        userService.register(dto);

        verify(rbacService).assignDefaultRole(12L);
        verify(passwordEncoder).encode("plain-password");
    }

    @Test
    void loginShouldReturnCurrentDatabaseRolesAndPermissions() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("plain-password");
        User current = user(2L, "alice", "ADMIN", 4L);
        current.setPassword("encoded-password");
        when(userMapper.selectOne(any())).thenReturn(current);
        when(passwordEncoder.matches("plain-password", "encoded-password"))
                .thenReturn(true);
        when(rbacService.getAuthoritySnapshot(2L))
                .thenReturn(new AuthoritySnapshot(
                        Set.of("USER", "ADMIN"),
                        Set.of("resource:read", "resource:audit")));

        LoginVO result = userService.login(dto);

        assertEquals("ADMIN", result.getRole());
        assertEquals(Set.of("USER", "ADMIN"), result.getRoles());
        assertEquals(Set.of("resource:read", "resource:audit"),
                result.getPermissions());
        verify(tokenSessionService).storeRefreshToken(
                result.getRefreshToken(),
                2L,
                4L);
    }

    @Test
    void accessTokenCannotCallRefreshFlow() {
        String accessToken = jwtUtil.generateAccessToken(2L, "alice", "USER", 0L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.refresh(accessToken));

        assertEquals(401, exception.getCode());
    }

    @Test
    void roleChangeMustIncrementVersionAndRevokeSessions() {
        userService.updateUserRole(2L, "USER", 1L);

        verify(rbacService).replaceUserRolesByCodes(
                2L,
                List.of("USER"),
                1L);
    }

    @Test
    void passwordChangeMustInvalidateAllExistingTokens() {
        User target = user(2L, "alice", "USER", 1L);
        target.setPassword("encoded-old");
        when(userMapper.selectById(2L)).thenReturn(target);
        when(passwordEncoder.matches("old-password", "encoded-old")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "encoded-old")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new");
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setOldPassword("old-password");
        dto.setNewPassword("new-password");

        userService.changePassword(2L, dto);

        assertEquals("encoded-new", target.getPassword());
        assertEquals(2L, target.getTokenVersion());
        verify(tokenSessionService).revokeAll(2L);
    }

    @Test
    void logoutMustBlacklistCurrentAccessTokenAndRevokeAllSessions() {
        User target = user(2L, "alice", "USER", 3L);
        when(userMapper.selectById(2L)).thenReturn(target);
        String accessToken = jwtUtil.generateAccessToken(2L, "alice", "USER", 3L);

        userService.logout(2L, accessToken);

        assertEquals(4L, target.getTokenVersion());
        verify(tokenSessionService).blacklistAccessToken(accessToken);
        verify(tokenSessionService).syncUserVersion(2L, 4L);
        verify(tokenSessionService).revokeAll(2L);
        verify(userMapper).updateById(target);
    }

    private User user(Long id, String username, String role, Long tokenVersion) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username);
        user.setStatus(1);
        user.setDeleted(0);
        user.setTokenVersion(tokenVersion);
        return user;
    }
}
