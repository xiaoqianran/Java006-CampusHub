package com.shiqian.resource.security;

import com.shiqian.common.result.Result;
import com.shiqian.common.security.AuthoritySnapshot;
import com.shiqian.common.security.TokenKey;
import com.shiqian.resource.client.UserPublicProfileClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthorityProviderTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private UserPublicProfileClient userClient;

    private UserAuthorityProvider provider;

    @BeforeEach
    void setUp() {
        provider = new UserAuthorityProvider(redisTemplate, userClient);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void shouldUseSharedRedisAuthoritySnapshotOnCacheHit() {
        when(setOperations.members(TokenKey.userAuthorities(7L)))
                .thenReturn(Set.of("ROLE_ADMIN", "resource:audit"));

        AuthoritySnapshot snapshot = provider.getAuthorities(7L);

        assertEquals(Set.of("ADMIN"), snapshot.getRoles());
        assertEquals(Set.of("resource:audit"), snapshot.getPermissions());
        verify(userClient, never()).getAuthorities(7L);
    }

    @Test
    void shouldFetchUserServiceOnCacheMiss() {
        when(setOperations.members(TokenKey.userAuthorities(7L)))
                .thenReturn(Set.of());
        AuthoritySnapshot remote = new AuthoritySnapshot(
                Set.of("USER"),
                Set.of("resource:read"));
        when(userClient.getAuthorities(7L)).thenReturn(Result.ok(remote));

        AuthoritySnapshot snapshot = provider.getAuthorities(7L);

        assertEquals(remote, snapshot);
        verify(userClient).getAuthorities(7L);
    }

    @Test
    void shouldFailClosedWhenCacheAndUserServiceAreUnavailable() {
        when(setOperations.members(TokenKey.userAuthorities(7L)))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(userClient.getAuthorities(7L))
                .thenReturn(Result.fail(503, "user service unavailable"));

        AuthoritySnapshot snapshot = provider.getAuthorities(7L);

        assertTrue(snapshot.asGrantedAuthorities().isEmpty());
    }
}
