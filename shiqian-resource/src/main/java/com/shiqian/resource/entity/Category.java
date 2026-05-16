package com.shiqian.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String name;

    private Integer sortOrder;

    private String icon;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private List<Category> children;
}
