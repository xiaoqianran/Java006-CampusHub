package com.shiqian.resource.dto;

import lombok.Data;

@Data
public class AttachmentCreateDTO {

    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
    private String mimeType;
    private String assetKind;   // IMAGE / VIDEO / DOCUMENT / ARCHIVE / CODE / OTHER
    private String usageType;   // INLINE / ATTACHMENT
    private Integer sortOrder;
}
