package com.shiqian.resource.config;

import com.shiqian.resource.cache.CacheNames;
import com.shiqian.resource.cache.ResourceCacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Redis 配置单元测试
 */
class RedisConfigTest {

    private final RedisConfig redisConfig = new RedisConfig();

    @Test
    void testRedisTemplateConfiguration() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisTemplate<String, Object> template = redisConfig.redisTemplate(connectionFactory);

        assertNotNull(template, "RedisTemplate 不能为空");
        assertInstanceOf(StringRedisSerializer.class, template.getKeySerializer(),
                "KeySerializer 必须是 StringRedisSerializer");
        assertInstanceOf(GenericJackson2JsonRedisSerializer.class, template.getValueSerializer(),
                "ValueSerializer 必须是 GenericJackson2JsonRedisSerializer");
        assertInstanceOf(StringRedisSerializer.class, template.getHashKeySerializer(),
                "HashKeySerializer 必须是 StringRedisSerializer");
        assertInstanceOf(GenericJackson2JsonRedisSerializer.class, template.getHashValueSerializer(),
                "HashValueSerializer 必须是 GenericJackson2JsonRedisSerializer");
    }

    @Test
    void testCacheManagerConfiguration() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        ResourceCacheProperties properties = new ResourceCacheProperties();
        RedisCacheManager cacheManager =
                (RedisCacheManager) redisConfig.cacheManager(connectionFactory, properties);
        cacheManager.initializeCaches();

        assertNotNull(cacheManager, "CacheManager 不能为空");
        assertTrue(cacheManager.isTransactionAware(), "CacheManager 必须启用事务感知");

        assertNotNull(cacheManager.getCache(CacheNames.RESOURCE_DETAIL));
        assertNotNull(cacheManager.getCache(CacheNames.CATEGORY_TREE));
    }
}
