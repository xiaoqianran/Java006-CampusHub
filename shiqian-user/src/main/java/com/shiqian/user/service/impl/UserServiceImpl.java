package com.shiqian.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.user.dto.LoginDTO;
import com.shiqian.user.dto.LoginVO;
import com.shiqian.user.dto.RegisterDTO;
import com.shiqian.user.dto.UpdateUserDTO;
import com.shiqian.user.dto.UserInfoVO;
import com.shiqian.user.entity.User;
import com.shiqian.user.mapper.UserMapper;
import com.shiqian.user.service.UserService;
import com.shiqian.common.security.JwtUtil;
import com.shiqian.common.security.RoleEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import io.jsonwebtoken.Claims;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

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
        user.setRole(RoleEnum.USER.name());
        user.setStatus(1);

        userMapper.insert(user);
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

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());

        LoginVO loginVO = new LoginVO();
        loginVO.setAccessToken(accessToken);
        loginVO.setRefreshToken(refreshToken);
        loginVO.setUserId(user.getId());
        loginVO.setUsername(user.getUsername());
        loginVO.setNickname(user.getNickname());
        loginVO.setRole(user.getRole());

        return loginVO;
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
        String role = claims.get("role", String.class);

        if (userId == null || !StringUtils.hasText(username)) {
            throw new BusinessException(401, "refreshToken 无效");
        }

        // 安全校验：用户仍存在、未删除、启用状态
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1 || user.getStatus() != 1
                || !username.equals(user.getUsername())) {
            throw new BusinessException(401, "用户状态异常，请重新登录");
        }

        String newAccessToken = jwtUtil.generateAccessToken(userId, username, role != null ? role : user.getRole());
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, username, role != null ? role : user.getRole());

        LoginVO loginVO = new LoginVO();
        loginVO.setAccessToken(newAccessToken);
        loginVO.setRefreshToken(newRefreshToken);
        loginVO.setUserId(userId);
        loginVO.setUsername(username);
        loginVO.setNickname(user.getNickname());
        loginVO.setRole(user.getRole());

        return loginVO;
    }

    @Override
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
    public UserInfoVO getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(404, "用户不存在");
        }
        return toUserInfoVO(user);
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

        Page<UserInfoVO> result = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        result.setRecords(userPage.getRecords().stream().map(this::toUserInfoVO).toList());
        return result;
    }

    private UserInfoVO toUserInfoVO(User user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    @Override
    public void updateUserStatus(Long targetUserId, Integer status, Long operatorId) {
        if (targetUserId == null || status == null) {
            throw new BusinessException("参数错误");
        }
        if (targetUserId.equals(operatorId)) {
            throw new BusinessException(400, "不能修改自己的状态");
        }

        User user = userMapper.selectById(targetUserId);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(404, "用户不存在");
        }

        // 防止禁用最后一个管理员
        if (status == 0 && "ADMIN".equals(user.getRole())) {
            long adminCount = userMapper.selectCount(
                new QueryWrapper<User>().eq("role", "ADMIN").eq("status", 1).eq("deleted", 0)
            );
            if (adminCount <= 1) {
                throw new BusinessException(400, "至少保留一个启用的管理员账号");
            }
        }

        user.setStatus(status);
        userMapper.updateById(user);
        log.info("管理员{} {}了用户{} (status={})", operatorId, status == 1 ? "启用" : "禁用", targetUserId, status);
    }
}
