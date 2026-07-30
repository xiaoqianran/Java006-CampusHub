package com.shiqian.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.user.dto.LoginDTO;
import com.shiqian.user.dto.LoginVO;
import com.shiqian.user.dto.RegisterDTO;
import com.shiqian.user.dto.UpdateUserDTO;
import com.shiqian.user.dto.UserInfoVO;
import com.shiqian.user.dto.ChangePasswordDTO;
import com.shiqian.user.entity.User;
import com.shiqian.user.mapper.UserMapper;
import com.shiqian.user.service.UserService;
import com.shiqian.user.service.TokenSessionService;
import com.shiqian.user.service.RbacService;
import com.shiqian.common.security.AuthoritySnapshot;
import com.shiqian.common.security.JwtUtil;
import com.shiqian.common.user.PublicUserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import io.jsonwebtoken.Claims;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenSessionService tokenSessionService;
    private final RbacService rbacService;

    @Override
    public boolean checkDatabaseConnection() {
        try {
            userMapper.selectCount(null);
            return true;
        } catch (Exception e) {
            log.error("Database connection check failed", e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO registerDTO) {
        if (checkUsernameExists(registerDTO.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (registerDTO.getEmail() != null && !registerDTO.getEmail().isEmpty()) {
            if (checkEmailExists(registerDTO.getEmail())) {
                throw new BusinessException("邮箱已被注册");
            }
        }
        if (registerDTO.getPhone() != null && !registerDTO.getPhone().isEmpty()) {
            if (checkPhoneExists(registerDTO.getPhone())) {
                throw new BusinessException("手机号已被注册");
            }
        }

        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNickname(registerDTO.getNickname() != null && !registerDTO.getNickname().isEmpty()
                ? registerDTO.getNickname()
                : registerDTO.getUsername());
        user.setEmail(registerDTO.getEmail());
        user.setPhone(registerDTO.getPhone());
        user.setStatus(1);
        user.setTokenVersion(0L);

        userMapper.insert(user);
        rbacService.assignDefaultRole(user.getId());
    }

    private boolean checkUsernameExists(String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username).eq("deleted", 0);
        return userMapper.selectCount(wrapper) > 0;
    }

    private boolean checkEmailExists(String email) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("email", email).eq("deleted", 0);
        return userMapper.selectCount(wrapper) > 0;
    }

    private boolean checkPhoneExists(String phone) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("phone", phone).eq("deleted", 0);
        return userMapper.selectCount(wrapper) > 0;
    }

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", loginDTO.getUsername()).eq("deleted", 0);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        return issueTokenPair(user);
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException("refreshToken 不能为空");
        }

        Claims claims = jwtUtil.parseToken(refreshToken);
        if (claims == null) {
            throw new BusinessException(401, "refreshToken 无效或已过期");
        }

        Long userId = claims.get("userId", Long.class);
        String username = claims.get("username", String.class);
        String tokenType = claims.get("tokenType", String.class);
        String jti = claims.getId();
        Long claimedVersion = jwtUtil.getLongClaim(claims, "tokenVersion");

        if (userId == null || !StringUtils.hasText(username)
                || !com.shiqian.common.security.TokenType.REFRESH.name().equals(tokenType)
                || !StringUtils.hasText(jti)
                || claimedVersion == null) {
            throw new BusinessException(401, "refreshToken 无效");
        }

        // 安全校验：用户仍存在、未删除、启用状态
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1 || user.getStatus() != 1
                || !username.equals(user.getUsername())
                || !normalizeTokenVersion(user.getTokenVersion()).equals(claimedVersion)) {
            throw new BusinessException(401, "用户状态异常，请重新登录");
        }

        tokenSessionService.consumeRefreshToken(refreshToken, userId, jti);
        // 必须使用数据库中的最新角色签发，禁止继承旧 Refresh Token 的角色。
        return issueTokenPair(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(Long userId, String accessToken) {
        if (userId == null || !StringUtils.hasText(accessToken)) {
            throw new BusinessException(401, "未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(404, "用户不存在");
        }
        tokenSessionService.blacklistAccessToken(accessToken);
        invalidateUserTokens(user);
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserInfo(Long userId, UpdateUserDTO updateUserDTO) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (updateUserDTO.getEmail() != null && !updateUserDTO.getEmail().isEmpty()) {
            if (!updateUserDTO.getEmail().equals(user.getEmail()) && checkEmailExists(updateUserDTO.getEmail())) {
                throw new BusinessException("邮箱已被其他用户注册");
            }
            user.setEmail(updateUserDTO.getEmail());
        }

        if (updateUserDTO.getPhone() != null && !updateUserDTO.getPhone().isEmpty()) {
            if (!updateUserDTO.getPhone().equals(user.getPhone()) && checkPhoneExists(updateUserDTO.getPhone())) {
                throw new BusinessException("手机号已被其他用户注册");
            }
            user.setPhone(updateUserDTO.getPhone());
        }

        if (updateUserDTO.getNickname() != null) {
            user.setNickname(updateUserDTO.getNickname());
        }

        if (updateUserDTO.getAvatar() != null) {
            user.setAvatar(updateUserDTO.getAvatar());
        }

        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordDTO changePasswordDTO) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1 || user.getStatus() != 1) {
            throw new BusinessException(404, "用户不存在或已被禁用");
        }
        if (!passwordEncoder.matches(changePasswordDTO.getOldPassword(), user.getPassword())) {
            throw new BusinessException(400, "原密码错误");
        }
        if (passwordEncoder.matches(changePasswordDTO.getNewPassword(), user.getPassword())) {
            throw new BusinessException(400, "新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(changePasswordDTO.getNewPassword()));
        invalidateUserTokens(user);
        userMapper.updateById(user);
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(404, "用户不存在");
        }
        return toUserInfoVO(user, rbacService.getAuthoritySnapshot(userId));
    }

    @Override
    public List<PublicUserProfile> getPublicProfiles(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> uniqueIds = new LinkedHashSet<>(userIds).stream()
                .filter(id -> id != null && id > 0)
                .limit(200)
                .toList();
        if (uniqueIds.isEmpty()) {
            return List.of();
        }

        Map<Long, User> usersById = userMapper.selectBatchIds(uniqueIds).stream()
                .filter(user -> user.getDeleted() == null || user.getDeleted() == 0)
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return uniqueIds.stream()
                .map(usersById::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toPublicUserProfile)
                .toList();
    }

    private PublicUserProfile toPublicUserProfile(User user) {
        return new PublicUserProfile(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar());
    }

    @Override
    public Page<UserInfoVO> pageUsers(Integer page, Integer size, String keyword) {
        Page<User> pageParam = new Page<>(page, size);
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("username", keyword)
                    .or()
                    .like("nickname", keyword)
                    .or()
                    .like("email", keyword));
        }
        wrapper.orderByDesc("create_time");
        Page<User> userPage = userMapper.selectPage(pageParam, wrapper);

        Map<Long, Set<String>> rolesByUser = rbacService.getRoleCodesByUserIds(
                userPage.getRecords().stream().map(User::getId).toList());
        Page<UserInfoVO> result = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        result.setRecords(userPage.getRecords().stream()
                .map(user -> toUserInfoVO(
                        user,
                        new AuthoritySnapshot(
                                rolesByUser.getOrDefault(user.getId(), Set.of()),
                                Set.of())))
                .toList());
        return result;
    }

    private UserInfoVO toUserInfoVO(User user, AuthoritySnapshot snapshot) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setRole(toCompatibilityRole(snapshot));
        vo.setRoles(copyOf(snapshot.getRoles()));
        vo.setPermissions(copyOf(snapshot.getPermissions()));
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long targetUserId, Integer status, Long operatorId) {
        if (targetUserId == null || status == null) {
            throw new BusinessException("参数错误");
        }
        if (targetUserId.equals(operatorId)) {
            throw new BusinessException(400, "不能修改自己的状态");
        }
        if (status != 0 && status != 1) {
            throw new BusinessException(400, "用户状态只能是 0 或 1");
        }

        User user = userMapper.selectById(targetUserId);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(404, "用户不存在");
        }

        if (status == 0) {
            rbacService.assertCanDisableUser(targetUserId);
        }

        user.setStatus(status);
        invalidateUserTokens(user);
        userMapper.updateById(user);
        log.info("管理员{} {}了用户{} (status={})", operatorId, status == 1 ? "启用" : "禁用", targetUserId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserRole(Long targetUserId, String role, Long operatorId) {
        if (targetUserId == null || role == null || role.trim().isEmpty()) {
            throw new BusinessException("参数错误");
        }
        if (operatorId != null && targetUserId.equals(operatorId)) {
            throw new BusinessException(400, "不能修改自己的角色");
        }

        String newRole = role.trim().toUpperCase();
        if (!"USER".equals(newRole) && !"ADMIN".equals(newRole)) {
            throw new BusinessException(400, "角色只能是 USER 或 ADMIN");
        }

        rbacService.replaceUserRolesByCodes(
                targetUserId,
                List.of(newRole),
                operatorId);
        log.info("管理员{} 将用户{} 的角色修改为 {}", operatorId, targetUserId, newRole);
    }

    private LoginVO issueTokenPair(User user) {
        AuthoritySnapshot snapshot = rbacService.getAuthoritySnapshot(user.getId());
        if (snapshot.getRoles() == null || snapshot.getRoles().isEmpty()) {
            throw new BusinessException(403, "用户未分配有效角色");
        }
        String compatibilityRole = toCompatibilityRole(snapshot);
        Long tokenVersion = normalizeTokenVersion(user.getTokenVersion());
        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getUsername(), compatibilityRole, tokenVersion);
        String refreshToken = jwtUtil.generateRefreshToken(
                user.getId(), user.getUsername(), compatibilityRole, tokenVersion);
        tokenSessionService.storeRefreshToken(refreshToken, user.getId(), tokenVersion);

        LoginVO loginVO = new LoginVO();
        loginVO.setAccessToken(accessToken);
        loginVO.setRefreshToken(refreshToken);
        loginVO.setUserId(user.getId());
        loginVO.setUsername(user.getUsername());
        loginVO.setNickname(user.getNickname());
        loginVO.setRole(compatibilityRole);
        loginVO.setRoles(copyOf(snapshot.getRoles()));
        loginVO.setPermissions(copyOf(snapshot.getPermissions()));
        return loginVO;
    }

    private String toCompatibilityRole(AuthoritySnapshot snapshot) {
        Set<String> roles = snapshot != null && snapshot.getRoles() != null
                ? snapshot.getRoles()
                : Set.of();
        return roles.contains("SUPER_ADMIN") || roles.contains("ADMIN")
                ? "ADMIN"
                : "USER";
    }

    private Set<String> copyOf(Set<String> values) {
        return values == null
                ? Set.of()
                : new LinkedHashSet<>(values);
    }

    private void invalidateUserTokens(User user) {
        Long nextVersion = normalizeTokenVersion(user.getTokenVersion()) + 1;
        user.setTokenVersion(nextVersion);
        // 先提升版本并撤销 Refresh Token；Redis 失败时数据库事务回滚，避免状态半更新。
        tokenSessionService.syncUserVersion(user.getId(), nextVersion);
        tokenSessionService.revokeAll(user.getId());
    }

    private Long normalizeTokenVersion(Long tokenVersion) {
        return tokenVersion != null && tokenVersion >= 0 ? tokenVersion : 0L;
    }
}
