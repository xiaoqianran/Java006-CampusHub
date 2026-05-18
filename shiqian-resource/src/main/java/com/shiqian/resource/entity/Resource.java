package com.shiqian.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_resource")
public class Resource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String summary;

    private String contentMarkdown;

    private String contentType;   // MARKDOWN / HTML 等，默认为 MARKDOWN

    private Long categoryId;

    // 以下旧字段保留兼容，未来逐步废弃
    private String fileUrl;
    private Long fileSize;
    private String fileType;

    private Integer downloadCount;

    private Integer version;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
