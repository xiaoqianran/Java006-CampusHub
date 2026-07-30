package com.shiqian.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleUpdateDTO {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "角色说明不能超过500个字符")
    private String description;

    private Integer status;
}
