package com.shiqian.resource.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TagVO {

    private Long id;
    private String name;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
