package com.shiqian.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.user.dto.RegisterDTO;
import com.shiqian.user.entity.User;
import com.shiqian.user.mapper.UserMapper;
import com.shiqian.user.service.UserService;
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
}
