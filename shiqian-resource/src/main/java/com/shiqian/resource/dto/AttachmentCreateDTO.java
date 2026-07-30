package com.shiqian.resource.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AttachmentCreateDTO {

    @NotBlank(message = "附件名称不能为空")
    @Size(max = 255, message = "附件名称最多255个字符")
    private String fileName;

    @NotBlank(message = "附件地址不能为空")
    @Size(max = 500, message = "附件地址最多500个字符")
    private String fileUrl;

    @Min(value = 0, message = "附件大小不能为负数")
    private Long fileSize;

    @Size(max = 100, message = "附件类型最多100个字符")
    private String fileType;

    @Size(max = 100, message = "附件 MIME 类型最多100个字符")
    private String mimeType;
    private String assetKind;   // IMAGE / VIDEO / DOCUMENT / ARCHIVE / CODE / OTHER
    private String usageType;   // INLINE / ATTACHMENT
    private Integer sortOrder;
}
