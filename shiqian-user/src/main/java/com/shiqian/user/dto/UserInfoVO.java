package com.shiqian.user.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class UserInfoVO {

    private Long userId;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;

    private String role;

    private Set<String> roles;

    private Set<String> permissions;

    private Integer status;

    private LocalDateTime createTime;
}
