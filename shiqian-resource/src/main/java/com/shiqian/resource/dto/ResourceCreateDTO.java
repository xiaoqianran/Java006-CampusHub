package com.shiqian.resource.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ResourceCreateDTO {

    @NotBlank(message = "资源标题不能为空")
    @Size(max = 200, message = "资源标题最多200个字符")
    private String title;

    @Size(max = 500, message = "资源摘要最多500个字符")
    private String summary;

    private String contentMarkdown;

    @Size(max = 30, message = "内容类型最多30个字符")
    private String contentType;

    @NotNull(message = "分类ID不能为空")
    @Min(value = 1, message = "分类ID必须大于0")
    private Long categoryId;

    // 以下字段保留向后兼容，未来会迁移到 resource_attachment 表
    @Size(max = 500, message = "文件地址最多500个字符")
    private String fileUrl;

    @Min(value = 0, message = "文件大小不能为负数")
    private Long fileSize;

    @Size(max = 100, message = "文件类型最多100个字符")
    private String fileType;

    // 附件列表（第二阶段正式支持）
    private List<AttachmentCreateDTO> attachments;
}
