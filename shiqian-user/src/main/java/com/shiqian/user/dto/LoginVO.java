package com.shiqian.user.dto;

import lombok.Data;

import java.util.Set;

@Data
public class LoginVO {

    private String accessToken;

    private String refreshToken;

    private Long userId;

    private String username;

    private String nickname;

    private String role;

    private Set<String> roles;

    private Set<String> permissions;
}
