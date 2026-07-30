package com.shiqian.common.user;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 服务间批量查询用户公开资料的请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUserProfileRequest {

    @NotEmpty(message = "userIds 不能为空")
    @Size(max = 200, message = "单次最多查询 200 个用户")
    private List<@Positive(message = "用户ID必须为正整数") Long> userIds;
}
