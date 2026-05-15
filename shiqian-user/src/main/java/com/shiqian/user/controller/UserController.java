package com.shiqian.user.controller;

import com.shiqian.common.result.Result;
import com.shiqian.user.dto.LoginDTO;
import com.shiqian.user.dto.LoginVO;
import com.shiqian.user.dto.RegisterDTO;
import com.shiqian.user.dto.UpdateUserDTO;
import com.shiqian.user.entity.LoginUser;
import com.shiqian.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.ok();
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO loginDTO) {
        LoginVO loginVO = userService.login(loginDTO);
        return Result.ok(loginVO);
    }

    @PutMapping("/me")
    public Result<Void> updateCurrentUser(@RequestBody @Valid UpdateUserDTO updateUserDTO) {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        userService.updateUserInfo(loginUser.getUserId(), updateUserDTO);
        return Result.ok();
    }
}
