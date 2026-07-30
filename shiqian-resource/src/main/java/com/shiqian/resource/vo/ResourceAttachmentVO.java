package com.shiqian.resource.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResourceAttachmentVO {

    private Long id;
    private Long resourceId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
    private String mimeType;
    private String assetKind;
    private String usageType;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
