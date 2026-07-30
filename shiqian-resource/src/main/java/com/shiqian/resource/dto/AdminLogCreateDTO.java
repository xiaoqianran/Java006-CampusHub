package com.shiqian.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminLogCreateDTO {

    @NotBlank(message = "操作类型不能为空")
    @Size(max = 100, message = "操作类型不能超过100个字符")
    private String action;

    @Positive(message = "目标ID必须大于0")
    private Long targetId;

    @Size(max = 2000, message = "操作详情不能超过2000个字符")
    private String detail;
}
