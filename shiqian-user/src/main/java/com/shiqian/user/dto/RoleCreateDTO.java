package com.shiqian.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleCreateDTO {

    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "角色编码格式不正确")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "角色说明不能超过500个字符")
    private String description;
}
