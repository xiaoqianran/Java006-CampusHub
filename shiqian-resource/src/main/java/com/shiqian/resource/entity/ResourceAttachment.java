package com.shiqian.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_resource_attachment")
public class ResourceAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resourceId;

    private String fileName;

    private String fileUrl;

    private Long fileSize;

    private String fileType;

    private String mimeType;

    private String assetKind;     // IMAGE / VIDEO / DOCUMENT / ARCHIVE / CODE / OTHER

    private String usageType;     // ATTACHMENT / INLINE / COVER

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
