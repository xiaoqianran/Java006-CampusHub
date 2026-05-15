package com.shiqian.user.dto;

import lombok.Data;

@Data
public class LoginVO {

    private String accessToken;

    private String refreshToken;

    private Long userId;

    private String username;

    private String nickname;

    private String role;
}
