package com.shiqian.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.user.dto.LoginDTO;
import com.shiqian.user.dto.LoginVO;
import com.shiqian.user.dto.RegisterDTO;
import com.shiqian.user.dto.UpdateUserDTO;
import com.shiqian.user.dto.UserInfoVO;

public interface UserService {

    boolean checkDatabaseConnection();

    void register(RegisterDTO registerDTO);

    LoginVO login(LoginDTO loginDTO);

    LoginVO refresh(String refreshToken);

    void updateUserInfo(Long userId, UpdateUserDTO updateUserDTO);

    UserInfoVO getUserInfo(Long userId);

    Page<UserInfoVO> pageUsers(Integer page, Integer size, String keyword);

    void updateUserStatus(Long targetUserId, Integer status, Long operatorId);

    void updateUserRole(Long targetUserId, String role, Long operatorId);
}
