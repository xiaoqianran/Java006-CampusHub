package com.shiqian.user.controller;

import com.shiqian.common.result.Result;
import com.shiqian.common.user.BatchUserProfileRequest;
import com.shiqian.common.user.InternalApiHeaders;
import com.shiqian.common.user.PublicUserProfile;
import com.shiqian.user.security.InternalServiceKeyValidator;
import com.shiqian.user.service.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仅供后端服务调用，不通过 Gateway 对外路由。
 */
@Hidden
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;
    private final InternalServiceKeyValidator serviceKeyValidator;

    @PostMapping("/public-profiles/batch")
    public Result<List<PublicUserProfile>> getPublicProfiles(
            @RequestHeader(value = InternalApiHeaders.SERVICE_KEY, required = false)
            String serviceKey,
            @RequestBody @Valid BatchUserProfileRequest request) {
        serviceKeyValidator.validate(serviceKey);
        return Result.ok(userService.getPublicProfiles(request.getUserIds()));
    }
}
