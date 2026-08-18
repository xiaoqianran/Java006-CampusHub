package com.shiqian.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Set;

@Data
public class LoginVO {

    private String accessToken;

    /**
     * 仅供服务端写入 HttpOnly Cookie，禁止序列化到 JSON，避免浏览器 JavaScript 获取长期刷新令牌。
     */
    @JsonIgnore
    private String refreshToken;

    private Long userId;

    private String username;

    private String nickname;

    private String role;

    private Set<String> roles;

    private Set<String> permissions;
}
