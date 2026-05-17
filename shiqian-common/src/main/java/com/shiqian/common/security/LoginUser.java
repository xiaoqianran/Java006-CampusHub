package com.shiqian.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录用户信息
 */
@Data
@AllArgsConstructor
public class LoginUser {

    private Long userId;

    private String username;

    private String role;
}
