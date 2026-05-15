package com.shiqian.user.service;

import com.shiqian.user.dto.LoginDTO;
import com.shiqian.user.dto.LoginVO;
import com.shiqian.user.dto.RegisterDTO;
import com.shiqian.user.dto.UpdateUserDTO;

public interface UserService {

    boolean checkDatabaseConnection();

    void register(RegisterDTO registerDTO);

    LoginVO login(LoginDTO loginDTO);

    void updateUserInfo(Long userId, UpdateUserDTO updateUserDTO);
}
