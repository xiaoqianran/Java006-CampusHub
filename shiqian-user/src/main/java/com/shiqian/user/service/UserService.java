package com.shiqian.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.user.dto.LoginDTO;
import com.shiqian.user.dto.LoginVO;
import com.shiqian.user.dto.RegisterDTO;
import com.shiqian.user.dto.UpdateUserDTO;
import com.shiqian.user.dto.UserInfoVO;
import com.shiqian.user.dto.ChangePasswordDTO;
import com.shiqian.common.user.PublicUserProfile;

import java.util.List;

public interface UserService {

    boolean checkDatabaseConnection();

    void register(RegisterDTO registerDTO);

    LoginVO login(LoginDTO loginDTO);

    LoginVO refresh(String refreshToken);

    void logout(Long userId, String accessToken);

    void updateUserInfo(Long userId, UpdateUserDTO updateUserDTO);

    void changePassword(Long userId, ChangePasswordDTO changePasswordDTO);

    UserInfoVO getUserInfo(Long userId);

    List<PublicUserProfile> getPublicProfiles(List<Long> userIds);

    Page<UserInfoVO> pageUsers(Integer page, Integer size, String keyword);

    void updateUserStatus(Long targetUserId, Integer status, Long operatorId);

    void updateUserRole(Long targetUserId, String role, Long operatorId);
}
