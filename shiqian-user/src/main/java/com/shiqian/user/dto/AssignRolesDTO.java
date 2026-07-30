package com.shiqian.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AssignRolesDTO {

    @NotEmpty(message = "至少分配一个角色")
    @Size(max = 20, message = "单个用户最多分配20个角色")
    private List<@Positive(message = "角色ID必须为正整数") Long> roleIds;
}
