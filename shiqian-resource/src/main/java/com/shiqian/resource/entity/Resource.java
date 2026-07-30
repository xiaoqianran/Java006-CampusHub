package com.shiqian.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_resource")
public class Resource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    private String summary;

    private String contentMarkdown;

    private String contentType;   // ARTICLE / FILE / MIXED

    private String contentScene;  // BLOG / GALLERY / SHARE

    private String tags;          // 可选，自由标签，逗号分隔

    private String externalSource; // 外部导入来源，例如 JIMENG

    private String externalId;     // 外部来源内的唯一标识

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;

    // 以下旧字段保留兼容，未来逐步废弃
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    // 第二阶段：附件列表（不映射到数据库）
    @TableField(exist = false)
    private List<ResourceAttachment> attachments;

    // 轻量作者昵称（不映射数据库，通过 ResourceService 页查询富化）
    @TableField(exist = false)
    private String authorNickname;

    @TableField(exist = false)
    private String authorUsername;

    @TableField(exist = false)
    private String authorAvatar;
}
