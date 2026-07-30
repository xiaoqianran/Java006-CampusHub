package com.shiqian.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PermissionCreateDTO {

    @NotBlank(message = "权限编码不能为空")
    @Pattern(
            regexp = "^[a-z][a-z0-9_-]*:[a-z][a-z0-9_-]*$",
            message = "权限编码必须使用 resource:action 格式")
    private String code;

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 100, message = "权限名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "权限说明不能超过500个字符")
    private String description;
}
