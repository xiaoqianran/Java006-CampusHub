package com.shiqian.resource.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CategoryVO {

    private Long id;
    private Long parentId;
    private String name;
    private Integer sortOrder;
    private String icon;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<CategoryVO> children;
}
