package com.shiqian.resource.client;

import com.shiqian.common.result.Result;
import com.shiqian.common.security.AuthoritySnapshot;
import com.shiqian.common.user.BatchUserProfileRequest;
import com.shiqian.common.user.PublicUserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户服务不可用时返回明确的降级结果，由作者富化服务填入占位资料。
 */
@Slf4j
@Component
public class UserPublicProfileClientFallbackFactory
        implements FallbackFactory<UserPublicProfileClient> {

    @Override
    public UserPublicProfileClient create(Throwable cause) {
        log.warn("用户公开资料批量查询降级: {}", cause.toString());
        return new UserPublicProfileClient() {
            @Override
            public Result<List<PublicUserProfile>> getPublicProfiles(
                    BatchUserProfileRequest request) {
                return Result.fail(503, "用户服务暂不可用");
            }

            @Override
            public Result<AuthoritySnapshot> getAuthorities(Long userId) {
                return Result.fail(503, "用户权限服务暂不可用");
            }
        };
    }
}
