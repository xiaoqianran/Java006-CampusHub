package com.shiqian.resource.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员资源审核/运营决策。
 *
 * status: 1=发布，2=退回修改，3=拒绝，4=下架。
 */
@Data
public class ResourceReviewDTO {

    @Min(value = 1, message = "审核状态不合法")
    @Max(value = 4, message = "审核状态不合法")
    private Integer status;

    @Size(max = 500, message = "审核意见最多500个字符")
    private String reason;
}
