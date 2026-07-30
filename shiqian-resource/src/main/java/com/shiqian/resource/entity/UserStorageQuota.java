package com.shiqian.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_storage_quota")
public class UserStorageQuota {

    @TableId(type = IdType.INPUT)
    private Long ownerId;
    private Long usedBytes;
    private LocalDateTime updateTime;
}
