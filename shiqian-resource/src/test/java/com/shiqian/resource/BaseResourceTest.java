package com.shiqian.resource;

import com.shiqian.resource.repository.ResourceDocumentRepository;
import com.shiqian.resource.client.UserPublicProfileClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import com.shiqian.resource.security.AccessTokenVersionVerifier;
import com.shiqian.resource.security.UserAuthorityProvider;
import com.shiqian.resource.cache.CacheNames;
import com.shiqian.common.security.AuthoritySnapshot;
import org.junit.jupiter.api.BeforeEach;
import io.jsonwebtoken.Claims;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 资源模块测试基类，统一 mock 外部依赖 Bean
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseResourceTest {

    @MockBean
    protected ResourceDocumentRepository resourceDocumentRepository;

    @MockBean
    protected ElasticsearchOperations elasticsearchOperations;

    @MockBean
    protected RabbitTemplate rabbitTemplate;

    @MockBean
    protected AccessTokenVersionVerifier accessTokenVersionVerifier;

    @MockBean
    protected UserPublicProfileClient userPublicProfileClient;

    @MockBean
    protected UserAuthorityProvider userAuthorityProvider;

    @Autowired(required = false)
    protected CacheManager cacheManager;

    @Autowired(required = false)
    protected RedisConnectionFactory redisConnectionFactory;

    @BeforeEach
    void allowCurrentTestTokens() {
        when(accessTokenVersionVerifier.isCurrent(any(Claims.class))).thenReturn(true);
        stubUserAuthorities(1L);
        stubAdminAuthorities(2L);
        when(userAuthorityProvider.getAuthorities(eq(3L)))
                .thenReturn(new AuthoritySnapshot(Set.of("GUEST"), Set.of()));
        // transactionAware 缓存 + H2 回滚会导致按 id 的 Redis 缓存跨用例脏读，每测前清空测试库。
        flushRedisTestDatabase();
        clearCache(CacheNames.RESOURCE_DETAIL);
        clearCache(CacheNames.CATEGORY_TREE);
    }

    private void flushRedisTestDatabase() {
        if (redisConnectionFactory == null) {
            return;
        }
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        } catch (Exception ignored) {
            // Redis 不可用时退回 CacheManager.clear
        }
    }

    protected void stubUserAuthorities(Long userId) {
        when(userAuthorityProvider.getAuthorities(eq(userId)))
                .thenReturn(new AuthoritySnapshot(
                        Set.of("USER"),
                        Set.of(
                                "resource:read",
                                "resource:download",
                                "resource:favorite",
                                "resource:create",
                                "resource:update",
                                "resource:delete")));
    }

    protected void stubAdminAuthorities(Long userId) {
        when(userAuthorityProvider.getAuthorities(eq(userId)))
                .thenReturn(new AuthoritySnapshot(
                        Set.of("ADMIN"),
                        Set.of(
                                "resource:read",
                                "resource:download",
                                "resource:favorite",
                                "resource:create",
                                "resource:update",
                                "resource:delete",
                                "resource:audit",
                                "user:manage")));
    }

    private void clearCache(String cacheName) {
        if (cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
