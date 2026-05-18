package com.shiqian.resource.dto;

import lombok.Data;

@Data
public class FileUploadVO {

    private String originalName;

    private String fileUrl;

    private Long fileSize;

    private String fileType;
}
