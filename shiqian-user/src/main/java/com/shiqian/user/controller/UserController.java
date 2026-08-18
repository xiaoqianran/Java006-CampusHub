package com.shiqian.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.common.ratelimit.DistributedRateLimit;
import com.shiqian.common.ratelimit.RateLimitKeyMode;
import com.shiqian.common.result.Result;
import com.shiqian.common.security.LoginUser;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.user.dto.ChangePasswordDTO;
import com.shiqian.user.dto.LoginDTO;
import com.shiqian.user.dto.LoginVO;
import com.shiqian.user.dto.RegisterDTO;
import com.shiqian.user.dto.UpdateUserDTO;
import com.shiqian.user.dto.UserInfoVO;
import com.shiqian.user.dto.UserRoleUpdateDTO;
import com.shiqian.user.dto.UserStatusUpdateDTO;
import com.shiqian.user.security.RefreshTokenCookieService;
import com.shiqian.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "用户管理", description = "用户注册、登录、信息管理等接口")
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;
    private final RefreshTokenCookieService refreshTokenCookieService;

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
    @DistributedRateLimit(name = "user:register", limit = 5, windowSeconds = 3600, keyMode = RateLimitKeyMode.IP)
    public Result<Void> register(@RequestBody @Valid RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.ok();
    }

    @Operation(summary = "用户登录（refreshToken 仅写入 HttpOnly Cookie）")
    @PostMapping("/login")
    @DistributedRateLimit(name = "user:login", limit = 10, windowSeconds = 60, keyMode = RateLimitKeyMode.IP)
    public Result<LoginVO> login(
            @RequestBody @Valid LoginDTO loginDTO,
            HttpServletResponse response) {
        LoginVO loginVO = userService.login(loginDTO);
        refreshTokenCookieService.write(response, loginVO.getRefreshToken());
        disableSensitiveResponseCaching(response);
        return Result.ok(loginVO);
    }

    @Operation(summary = "刷新访问令牌（refreshToken 从 HttpOnly Cookie 读取并轮换）")
    @PostMapping("/refresh")
    @DistributedRateLimit(name = "user:refresh", limit = 30, windowSeconds = 60, keyMode = RateLimitKeyMode.IP)
    public Result<LoginVO> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = refreshTokenCookieService.read(request);
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(401, "refreshToken 缺失或已过期");
        }
        LoginVO loginVO = userService.refresh(refreshToken);
        refreshTokenCookieService.write(response, loginVO.getRefreshToken());
        disableSensitiveResponseCaching(response);
        return Result.ok(loginVO);
    }

    @Operation(summary = "退出登录（撤销当前访问令牌及该用户全部刷新令牌）")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletResponse response) {
        Long userId = SecurityUtil.getCurrentUserId();
        String accessToken = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : null;
        try {
            userService.logout(userId, accessToken);
            SecurityContextHolder.clearContext();
            return Result.ok();
        } finally {
            refreshTokenCookieService.clear(response);
            disableSensitiveResponseCaching(response);
        }
    }

    @Operation(summary = "更新当前用户信息")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/me")
    public Result<Void> updateCurrentUser(@RequestBody @Valid UpdateUserDTO updateUserDTO) {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        userService.updateUserInfo(loginUser.getUserId(), updateUserDTO);
        return Result.ok();
    }

    @Operation(summary = "修改当前用户密码（修改后所有旧令牌失效）")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/me/password")
    public Result<Void> changePassword(
            @RequestBody @Valid ChangePasswordDTO changePasswordDTO,
            HttpServletResponse response) {
        Long userId = SecurityUtil.getCurrentUserId();
        userService.changePassword(userId, changePasswordDTO);
        refreshTokenCookieService.clear(response);
        disableSensitiveResponseCaching(response);
        return Result.ok();
    }

    @Operation(summary = "获取当前用户信息")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public Result<UserInfoVO> getCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        return Result.ok(userService.getUserInfo(userId));
    }

    @Operation(summary = "管理员分页查询用户")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/users")
    @PreAuthorize("hasAuthority('user:manage')")
    public Result<Page<UserInfoVO>> pageUsers(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(userService.pageUsers(page, size, keyword));
    }

    @Operation(summary = "管理员修改用户状态（启用/禁用）")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/admin/users/{id}/status")
    @PreAuthorize("hasAuthority('user:manage')")
    public Result<Void> updateUserStatus(
            @PathVariable @Positive Long id,
            @RequestBody @Valid UserStatusUpdateDTO body) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        userService.updateUserStatus(id, body.getStatus(), operatorId);
        return Result.ok();
    }

    @Operation(summary = "管理员修改用户角色（USER/ADMIN）")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/admin/users/{id}/role")
    @PreAuthorize("hasAuthority('user:manage')")
    public Result<Void> updateUserRole(
            @PathVariable @Positive Long id,
            @RequestBody @Valid UserRoleUpdateDTO body) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        userService.updateUserRole(id, body.getRole(), operatorId);
        return Result.ok();
    }

    private void disableSensitiveResponseCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }
}
