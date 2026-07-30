package com.shiqian.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TagDTO {

    @NotBlank(message = "标签名称不能为空")
    @Size(max = 50, message = "标签名称最多50个字符")
    private String name;
}
