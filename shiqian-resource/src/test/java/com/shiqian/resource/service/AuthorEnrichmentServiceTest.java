package com.shiqian.resource.service;

import com.shiqian.common.result.Result;
import com.shiqian.common.user.BatchUserProfileRequest;
import com.shiqian.common.user.PublicUserProfile;
import com.shiqian.resource.client.UserPublicProfileClient;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.service.impl.AuthorEnrichmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorEnrichmentServiceTest {

    @Mock
    private UserPublicProfileClient userClient;

    private AuthorEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new AuthorEnrichmentServiceImpl(userClient);
    }

    @Test
    void shouldQueryDistinctAuthorsOnceAndFillMissingProfileWithPlaceholder() {
        Resource first = resource(7L);
        Resource duplicate = resource(7L);
        Resource missing = resource(9L);
        when(userClient.getPublicProfiles(
                org.mockito.ArgumentMatchers.any(BatchUserProfileRequest.class)))
                .thenReturn(Result.ok(List.of(
                        new PublicUserProfile(
                                7L,
                                "alice",
                                "小艾",
                                "https://example.com/alice.png"))));

        service.enrich(List.of(first, duplicate, missing));

        ArgumentCaptor<BatchUserProfileRequest> requestCaptor =
                ArgumentCaptor.forClass(BatchUserProfileRequest.class);
        verify(userClient, times(1)).getPublicProfiles(requestCaptor.capture());
        assertEquals(List.of(7L, 9L), requestCaptor.getValue().getUserIds());

        assertEquals("alice", first.getAuthorUsername());
        assertEquals("小艾", first.getAuthorNickname());
        assertEquals("https://example.com/alice.png", first.getAuthorAvatar());
        assertEquals("小艾", duplicate.getAuthorNickname());
        assertEquals("用户#9", missing.getAuthorUsername());
        assertEquals("用户#9", missing.getAuthorNickname());
        assertNull(missing.getAuthorAvatar());
    }

    @Test
    void shouldKeepResourceResponseAvailableWhenUserServiceThrows() {
        Resource resource = resource(12L);
        when(userClient.getPublicProfiles(
                org.mockito.ArgumentMatchers.any(BatchUserProfileRequest.class)))
                .thenThrow(new IllegalStateException("user service down"));

        service.enrich(List.of(resource));

        assertEquals("用户#12", resource.getAuthorUsername());
        assertEquals("用户#12", resource.getAuthorNickname());
    }

    @Test
    void shouldNotCallUserServiceForEmptyInput() {
        service.enrich(List.of());

        verifyNoInteractions(userClient);
    }

    private Resource resource(Long userId) {
        Resource resource = new Resource();
        resource.setUserId(userId);
        return resource;
    }
}
