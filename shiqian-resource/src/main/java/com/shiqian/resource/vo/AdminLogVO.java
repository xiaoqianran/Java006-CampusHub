package com.shiqian.resource.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminLogVO {

    private Long id;
    private Long operatorId;
    private String operatorName;
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
    private LocalDateTime createTime;
}
