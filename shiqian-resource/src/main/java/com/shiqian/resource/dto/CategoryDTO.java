package com.shiqian.resource.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryDTO {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 100, message = "分类名称最多100个字符")
    private String name;

    @NotNull(message = "父分类ID不能为空")
    @Min(value = 0, message = "父分类ID不能为负数")
    private Long parentId;

    @NotNull(message = "排序不能为空")
    @Min(value = 0, message = "排序不能为负数")
    private Integer sortOrder;

    @Size(max = 255, message = "图标URL最多255个字符")
    private String icon;

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态值无效")
    @Max(value = 1, message = "状态值无效")
    private Integer status;
}
