package com.shiqian.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_notification")
public class UserNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String messageId;
    private Long userId;
    private String notificationType;
    private String title;
    private String content;
    private Long relatedId;
    private Integer readFlag;
    private LocalDateTime createTime;
}
