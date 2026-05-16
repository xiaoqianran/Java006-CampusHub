package com.shiqian.resource.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResourceCreateDTO {

    @NotBlank(message = "资源标题不能为空")
    @Size(max = 200, message = "资源标题最多200个字符")
    private String title;

    @Size(max = 1000, message = "资源描述最多1000个字符")
    private String description;

    @NotNull(message = "分类ID不能为空")
    @Min(value = 1, message = "分类ID必须大于0")
    private Long categoryId;

    @NotBlank(message = "文件地址不能为空")
    @Size(max = 500, message = "文件地址最多500个字符")
    private String fileUrl;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 0, message = "文件大小不能为负数")
    private Long fileSize;

    @NotBlank(message = "文件类型不能为空")
    @Size(max = 100, message = "文件类型最多100个字符")
    private String fileType;
}
