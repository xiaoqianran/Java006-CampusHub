package com.shiqian.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_content_review_record")
public class ContentReviewRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resourceId;
    private Long submitterId;
    private Long reviewerId;
    private String reviewType;
    private String decision;
    private String matchedWords;
    private String reason;
    private String contentTitle;
    private LocalDateTime createTime;
}
