package com.shiqian.resource.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentReviewRecordVO {

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
