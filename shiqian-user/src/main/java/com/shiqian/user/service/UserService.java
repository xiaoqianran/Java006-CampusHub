package com.shiqian.user.service;

import com.shiqian.user.dto.RegisterDTO;

public interface UserService {

    boolean checkDatabaseConnection();

    void register(RegisterDTO registerDTO);
}
