package com.shiqian.user.service;

import com.shiqian.user.dto.LoginDTO;
import com.shiqian.user.dto.LoginVO;
import com.shiqian.user.dto.RegisterDTO;

public interface UserService {

    boolean checkDatabaseConnection();

    void register(RegisterDTO registerDTO);

    LoginVO login(LoginDTO loginDTO);
}
