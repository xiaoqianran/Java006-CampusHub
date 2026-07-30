package com.shiqian.resource.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ResourceVO {

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String summary;
    private String contentMarkdown;
    private String contentType;
    private String contentScene;
    private String tags;
    private String externalSource;
    private String externalId;
    private Long categoryId;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
    private Integer downloadCount;
    private Integer viewCount;
    private Integer version;
    private Integer status;
    private String reviewReason;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private String offlineReason;
    private LocalDateTime publishedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<ResourceAttachmentVO> attachments;
    private String authorNickname;
    private String authorUsername;
    private String authorAvatar;
    private List<Long> categoryIds;
    private List<String> categoryNames;
    private List<Long> tagIds;
    private List<String> tagNames;
    private Map<String, List<String>> searchHighlights;
}
