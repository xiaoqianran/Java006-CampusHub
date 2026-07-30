package com.shiqian.resource.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_resource_category")
public class ResourceCategory {

    private Long resourceId;

    private Long categoryId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
