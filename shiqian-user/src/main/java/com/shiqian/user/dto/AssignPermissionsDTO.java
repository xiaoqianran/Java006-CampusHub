package com.shiqian.user.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssignPermissionsDTO {

    @Size(max = 200, message = "单个角色最多分配200个权限")
    private List<@Positive(message = "权限ID必须为正整数") Long> permissionIds =
            new ArrayList<>();
}
