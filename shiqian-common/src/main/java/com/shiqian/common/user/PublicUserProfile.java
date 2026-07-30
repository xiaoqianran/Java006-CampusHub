package com.shiqian.common.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可跨服务传递的用户公开资料，不包含密码、联系方式、角色等敏感字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicUserProfile {

    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
}
