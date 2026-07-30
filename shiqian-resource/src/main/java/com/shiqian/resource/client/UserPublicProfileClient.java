package com.shiqian.resource.client;

import com.shiqian.common.result.Result;
import com.shiqian.common.user.BatchUserProfileRequest;
import com.shiqian.common.user.PublicUserProfile;
import com.shiqian.resource.config.UserClientFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "shiqian-user",
        contextId = "userPublicProfileClient",
        url = "${resource.user-client.url:}",
        path = "/internal/users",
        configuration = UserClientFeignConfig.class,
        fallbackFactory = UserPublicProfileClientFallbackFactory.class)
public interface UserPublicProfileClient {

    @PostMapping("/public-profiles/batch")
    Result<List<PublicUserProfile>> getPublicProfiles(
            @RequestBody BatchUserProfileRequest request);
}
