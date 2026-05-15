package com.shiqian.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.user.dto.LoginDTO;
import com.shiqian.user.dto.LoginVO;
import com.shiqian.user.dto.RegisterDTO;
import com.shiqian.user.dto.UpdateUserDTO;
import com.shiqian.user.entity.User;
import com.shiqian.user.mapper.UserMapper;
import com.shiqian.user.service.UserService;
import com.shiqian.user.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
        user.setRole("USER");
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
}
