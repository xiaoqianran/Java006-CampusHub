package com.shiqian.resource.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SensitiveWordDTO {
    @NotBlank(message = "敏感词不能为空")
    @Size(max = 100, message = "敏感词最多100个字符")
    private String word;
    @Min(value = 1, message = "级别必须为1到3")
    @Max(value = 3, message = "级别必须为1到3")
    private Integer level = 2;
    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status = 1;
}
