package com.shiqian.user.service;

import com.shiqian.user.dto.LoginDTO;
import com.shiqian.user.dto.LoginVO;
import com.shiqian.user.dto.RegisterDTO;
import com.shiqian.user.dto.UserInfoVO;

public interface UserService {

    boolean checkDatabaseConnection();

    void register(RegisterDTO registerDTO);

    LoginVO login(LoginDTO loginDTO);

    UserInfoVO getUserInfo(Long userId);
}
