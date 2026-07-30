package com.shiqian.resource.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SensitiveWordVO {

    private Long id;
    private String word;
    private Integer level;
    private Integer status;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
