package com.shiqian.resource.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_stored_object")
public class StoredObject {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String publicId;
    private Long ownerId;
    private Long resourceId;
    private String objectKey;
    private String originalName;
    private String storageProvider;
    private String bucketName;
    private Long fileSize;
    private String extension;
    private String mimeType;
    private String assetKind;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
