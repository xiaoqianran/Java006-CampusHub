package com.shiqian.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserRoleUpdateDTO {

    @NotBlank(message = "用户角色不能为空")
    @Pattern(regexp = "(?i)USER|ADMIN", message = "用户角色只能是USER或ADMIN")
    private String role;
}
