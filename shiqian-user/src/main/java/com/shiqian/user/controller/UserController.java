package com.shiqian.user.controller;

import com.shiqian.common.result.Result;
import com.shiqian.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        boolean dbConnected = userService.checkDatabaseConnection();
        Map<String, Object> data = new HashMap<>();
        data.put("service", "shiqian-user");
        data.put("status", "UP");
        data.put("database", dbConnected ? "CONNECTED" : "DISCONNECTED");
        data.put("timestamp", System.currentTimeMillis());
        return Result.ok(data);
    }
}
