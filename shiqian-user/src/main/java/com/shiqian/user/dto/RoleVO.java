package com.shiqian.user.dto;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class RoleVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Boolean systemRole;
    private Boolean superAdmin;
    private Integer status;
    private Set<String> permissionCodes = new LinkedHashSet<>();
}
