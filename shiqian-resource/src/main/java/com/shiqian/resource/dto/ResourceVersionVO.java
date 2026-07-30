package com.shiqian.resource.dto;

import com.shiqian.resource.entity.ResourceAttachment;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ResourceVersionVO {

    private Long id;
    private Long resourceId;
    private Integer versionNumber;
    private String title;
    private String summary;
    private String description;
    private String markdownContent;
    private List<Long> categoryIds;
    private List<String> tagNames;
    private String contentScene;
    private String resourceType;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
    private List<ResourceAttachment> attachments;
    private String changeDescription;
    private Long createdBy;
    private LocalDateTime createTime;
}
