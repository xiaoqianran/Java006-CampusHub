package com.shiqian.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_counter_flush_batch")
public class CounterFlushBatch {

    @TableId(type = IdType.INPUT)
    private String batchId;
    private String counterType;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
