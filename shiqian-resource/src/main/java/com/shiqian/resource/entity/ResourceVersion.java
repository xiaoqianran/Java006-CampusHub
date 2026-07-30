package com.shiqian.resource.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资源完整快照。分类、标签和附件使用 JSON 保存，保证回滚时关系也能精确恢复。
 */
@Data
@TableName("t_resource_version")
public class ResourceVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resourceId;

    private Integer versionNumber;

    private String title;

    private String summary;

    private String description;

    private String markdownContent;

    private Long categoryId;

    private String tags;

    private String contentScene;

    private String resourceType;

    private String fileUrl;

    private Long fileSize;

    private String fileType;

    private String categoryIdsJson;

    private String tagNamesJson;

    private String attachmentsJson;

    private String changeDescription;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
