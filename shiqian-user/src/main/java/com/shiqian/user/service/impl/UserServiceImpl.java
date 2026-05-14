package com.shiqian.user.service.impl;

import com.shiqian.user.mapper.UserMapper;
import com.shiqian.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

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
}
