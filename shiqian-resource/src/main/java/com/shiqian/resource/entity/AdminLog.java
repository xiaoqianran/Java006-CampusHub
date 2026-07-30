package com.shiqian.resource.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_admin_operation_log")
public class AdminLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operatorId;

    private String operatorName;

    @TableField("operation_type")
    private String action;

    private String targetType;

    private Long targetId;

    private String detail;

    private String requestMethod;

    private String requestUri;

    private String requestIp;

    private String requestParams;

    private String result;

    private String errorMessage;

    private Long durationMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
