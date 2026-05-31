package com.shiqian.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.result.Result;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.user.dto.LoginDTO;
import com.shiqian.user.dto.LoginVO;
import com.shiqian.user.dto.RegisterDTO;
import com.shiqian.user.dto.UpdateUserDTO;
import com.shiqian.user.dto.UserInfoVO;
import com.shiqian.common.security.LoginUser;
import com.shiqian.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "用户管理", description = "用户注册、登录、信息管理等接口")
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "健康检查")
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

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.ok();
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO loginDTO) {
        LoginVO loginVO = userService.login(loginDTO);
        return Result.ok(loginVO);
    }

    @Operation(summary = "刷新访问令牌（使用 refreshToken 重新签发 accessToken + refreshToken）")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body != null ? body.get("refreshToken") : null;
        LoginVO loginVO = userService.refresh(refreshToken);
        return Result.ok(loginVO);
    }

    @Operation(summary = "更新当前用户信息")
    @PutMapping("/me")
    public Result<Void> updateCurrentUser(@RequestBody @Valid UpdateUserDTO updateUserDTO) {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        userService.updateUserInfo(loginUser.getUserId(), updateUserDTO);
        return Result.ok();
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public Result<UserInfoVO> getCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        return Result.ok(userService.getUserInfo(userId));
    }

    @Operation(summary = "管理员分页查询用户")
    @GetMapping("/admin/users")
    @PreAuthorize("hasAuthority('user:manage')")
    public Result<Page<UserInfoVO>> pageUsers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(userService.pageUsers(page, size, keyword));
    }

    @Operation(summary = "管理员修改用户状态（启用/禁用）")
    @PutMapping("/admin/users/{id}/status")
    @PreAuthorize("hasAuthority('user:manage')")
    public Result<Void> updateUserStatus(
            @PathVariable Long id,
            @RequestBody @Valid java.util.Map<String, Integer> body) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        Integer status = body.get("status");
        userService.updateUserStatus(id, status, operatorId);
        return Result.ok();
    }
}
