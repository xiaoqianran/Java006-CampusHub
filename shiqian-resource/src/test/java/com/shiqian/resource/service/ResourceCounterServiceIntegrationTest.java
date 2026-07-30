package com.shiqian.resource.service;

import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.config.ResourceCounterProperties;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.ResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Transactional
class ResourceCounterServiceIntegrationTest extends BaseResourceTest {

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ResourceCounterService counterService;

    @Autowired
    private ResourceCounterProperties properties;

    @Autowired
    private ResourceMapper resourceMapper;

    private ValueOperations<String, String> valueOperations;
    private HashOperations<String, Object, Object> hashOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void configureRedisMocks() {
        reset(redisTemplate);
        properties.setEnabled(true);
        valueOperations = mock(ValueOperations.class);
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @AfterEach
    void disableScheduledAggregation() {
        properties.setEnabled(false);
    }

    @Test
    void sameViewerShouldOnlyIncrementOnceWithinDedupWindow() {
        Resource resource = publishedResource("浏览聚合");
        when(valueOperations.setIfAbsent(
                anyString(), eq("1"), any(Long.class), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true, false);

        assertTrue(counterService.recordView(resource.getId(), 99L, "192.0.2.1"));
        assertFalse(counterService.recordView(resource.getId(), 99L, "192.0.2.1"));

        verify(hashOperations).increment(
                "campushub:v1:counter:view:active",
                String.valueOf(resource.getId()),
                1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rotatedBatchShouldWriteMysqlOnceAndCreateIdempotencyRecord() {
        Resource resource = publishedResource("批量写回");
        Cursor<String> cursor = mock(Cursor.class);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class))).thenReturn(1L, 0L);
        when(hashOperations.entries(anyString())).thenReturn(Map.of(
                String.valueOf(resource.getId()), "3"));
        when(redisTemplate.delete(anyString())).thenReturn(true);

        counterService.flushPending();

        Resource updated = resourceMapper.selectById(resource.getId());
        assertEquals(3, updated.getViewCount());
        assertEquals(0, updated.getDownloadCount());
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void disabledAggregationShouldUseExistingTransactionalCounter() {
        Resource resource = publishedResource("降级计数");
        properties.setEnabled(false);

        counterService.recordDownload(resource.getId());

        assertEquals(1, resourceMapper.selectById(resource.getId()).getDownloadCount());
        verify(hashOperations, never()).increment(anyString(), anyString(), any(Long.class));
    }

    private Resource publishedResource(String title) {
        Resource resource = new Resource();
        resource.setUserId(1L);
        resource.setTitle(title);
        resource.setContentType("ARTICLE");
        resource.setContentScene("BLOG");
        resource.setFileSize(0L);
        resource.setDownloadCount(0);
        resource.setViewCount(0);
        resource.setVersion(1);
        resource.setStatus(1);
        resource.setPublishedTime(LocalDateTime.now());
        resource.setDeleted(0);
        resourceMapper.insert(resource);
        return resource;
    }
}
