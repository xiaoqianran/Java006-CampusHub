package com.shiqian.resource.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResourceRollbackDTO {

    @Size(max = 500, message = "回滚说明最多500个字符")
    private String changeDescription;
}
