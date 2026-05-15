package com.shiqian.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserInfoVO {

    private Long userId;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;

    private String role;

    private LocalDateTime createTime;
}
