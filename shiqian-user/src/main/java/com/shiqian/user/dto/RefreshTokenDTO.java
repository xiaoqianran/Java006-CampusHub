package com.shiqian.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefreshTokenDTO {

    @NotBlank(message = "refreshToken 不能为空")
    @Size(max = 4096, message = "refreshToken 格式不合法")
    private String refreshToken;
}
