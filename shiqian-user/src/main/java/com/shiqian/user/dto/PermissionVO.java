package com.shiqian.user.dto;

import lombok.Data;

@Data
public class PermissionVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Boolean systemPermission;
    private Integer status;
}
