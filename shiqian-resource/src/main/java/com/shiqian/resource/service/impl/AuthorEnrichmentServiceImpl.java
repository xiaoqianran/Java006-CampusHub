package com.shiqian.resource.service.impl;

import com.shiqian.common.result.Result;
import com.shiqian.common.user.BatchUserProfileRequest;
import com.shiqian.common.user.PublicUserProfile;
import com.shiqian.resource.client.UserPublicProfileClient;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.service.AuthorEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorEnrichmentServiceImpl implements AuthorEnrichmentService {

    private final UserPublicProfileClient userClient;

    @Override
    public void enrich(Collection<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            return;
        }
        List<Long> userIds = resources.stream()
                .filter(Objects::nonNull)
                .map(Resource::getUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf));

        Map<Long, PublicUserProfile> profiles = loadProfiles(userIds);
        resources.stream()
                .filter(Objects::nonNull)
                .forEach(resource -> applyProfile(resource, profiles.get(resource.getUserId())));
    }

    private Map<Long, PublicUserProfile> loadProfiles(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            Result<List<PublicUserProfile>> response =
                    userClient.getPublicProfiles(new BatchUserProfileRequest(userIds));
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.debug("用户公开资料查询返回降级结果");
                return Map.of();
            }
            return response.getData().stream()
                    .filter(Objects::nonNull)
                    .filter(profile -> profile.getUserId() != null
                            && userIds.contains(profile.getUserId()))
                    .collect(Collectors.toMap(
                            PublicUserProfile::getUserId,
                            Function.identity(),
                            (first, ignored) -> first));
        } catch (RuntimeException exception) {
            // 即使熔断器未接管某类异常，资源列表也必须可用。
            log.warn("用户公开资料查询异常，使用占位作者: {}",
                    exception.getClass().getSimpleName());
            return Map.of();
        }
    }

    private void applyProfile(Resource resource, PublicUserProfile profile) {
        String placeholder = resource.getUserId() != null
                ? "用户#" + resource.getUserId()
                : "匿名用户";
        if (profile == null) {
            resource.setAuthorUsername(placeholder);
            resource.setAuthorNickname(placeholder);
            resource.setAuthorAvatar(null);
            return;
        }

        String username = StringUtils.hasText(profile.getUsername())
                ? profile.getUsername()
                : placeholder;
        resource.setAuthorUsername(username);
        resource.setAuthorNickname(StringUtils.hasText(profile.getNickname())
                ? profile.getNickname()
                : username);
        resource.setAuthorAvatar(profile.getAvatar());
    }
}
