package com.shiqian.resource.service;

import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.cache.CacheNames;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.CategoryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ResourceService 缓存集成测试
 */
class ResourceServiceCacheTest extends BaseResourceTest {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        // Must clear outbox too: create/update/delete append PENDING events, and restarting
        // resource identity to 1 otherwise collides with leftover aggregate_id rows
        // (breaks ResourceTransactionIntegrationTest when it runs after this class).
        jdbcTemplate.execute("DELETE FROM t_outbox_event");
        jdbcTemplate.execute("DELETE FROM t_resource");
        jdbcTemplate.execute("DELETE FROM t_category");
        jdbcTemplate.execute("ALTER TABLE t_resource ALTER COLUMN id RESTART WITH 1");
        jdbcTemplate.execute("ALTER TABLE t_category ALTER COLUMN id RESTART WITH 1");
        Cache cache = cacheManager.getCache(CacheNames.RESOURCE_DETAIL);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void testGetResourceByIdCacheable() {
        Category category = new Category();
        category.setName("缓存测试分类");
        category.setParentId(0L);
        category.setStatus(1);
        category.setSortOrder(1);
        categoryMapper.insert(category);

        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("缓存测试资源");
        dto.setSummary("缓存测试摘要");
        dto.setContentMarkdown("缓存测试正文");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("/api/resource/files/1/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        Resource created = resourceService.createResource(1L, dto);
        assertNotNull(created.getId(), "资源创建后 ID 不能为空");
        Long id = created.getId();

        Resource first = resourceService.getResourceById(id);
        assertNotNull(first, "首次查询必须返回资源对象");
        assertEquals("缓存测试资源", first.getTitle());

        Cache cache = cacheManager.getCache(CacheNames.RESOURCE_DETAIL);
        assertNotNull(cache, "缓存对象不能为空");
        Cache.ValueWrapper wrapper = cache.get(id);
        assertNotNull(wrapper, "首次查询后缓存必须写入");
        assertNotNull(wrapper.get(), "缓存值不能为空");

        Resource second = resourceService.getResourceById(id);
        assertNotNull(second, "二次查询必须返回资源对象");
        assertEquals(first.getTitle(), second.getTitle());
    }

    @Test
    void testCacheEvictOnUpdateAndDelete() {
        Category category = new Category();
        category.setName("一致性测试分类");
        category.setParentId(0L);
        category.setStatus(1);
        category.setSortOrder(1);
        categoryMapper.insert(category);

        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("一致性测试资源");
        dto.setSummary("一致性测试摘要");
        dto.setContentMarkdown("一致性测试正文");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("/api/resource/files/1/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        Resource created = resourceService.createResource(1L, dto);
        Long id = created.getId();

        resourceService.getResourceById(id);
        Cache cache = cacheManager.getCache(CacheNames.RESOURCE_DETAIL);
        assertNotNull(cache);
        assertNotNull(cache.get(id), "更新前缓存必须存在");

        ResourceUpdateDTO updateDTO = new ResourceUpdateDTO();
        updateDTO.setTitle("更新后标题");
        updateDTO.setSummary("一致性测试摘要");
        updateDTO.setContentMarkdown("一致性测试正文");
        updateDTO.setCategoryId(category.getId());
        updateDTO.setFileUrl("/api/resource/files/1/file.pdf");
        updateDTO.setFileSize(1024L);
        updateDTO.setFileType("application/pdf");
        resourceService.updateResource(1L, id, updateDTO);

        assertNull(cache.get(id), "更新后缓存必须被清除");

        Resource updated = resourceService.getResourceById(id);
        assertEquals("更新后标题", updated.getTitle());
        assertNotNull(cache.get(id), "再次查询后缓存应重新写入");

        resourceService.deleteResource(1L, id);
        assertNull(cache.get(id), "删除后缓存必须被清除");
    }

    @Test
    void missingResourceShouldBeCachedAsShortLivedNull() {
        Long missingId = 999_999L;

        assertNull(resourceService.getResourceById(missingId));
        Cache cache = cacheManager.getCache(CacheNames.RESOURCE_DETAIL);
        assertNotNull(cache);
        assertNotNull(cache.get(missingId), "不存在的资源也应写入短 TTL 空值缓存");
        assertNull(cache.get(missingId).get());

        assertNull(resourceService.getResourceById(missingId));
    }

    @Test
    void viewAndDownloadCountersMustEvictStaleDetail() {
        Category category = new Category();
        category.setName("计数缓存分类");
        category.setParentId(0L);
        category.setStatus(1);
        category.setSortOrder(1);
        categoryMapper.insert(category);

        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("计数缓存资源");
        dto.setSummary("计数缓存摘要");
        dto.setContentMarkdown("计数缓存正文");
        dto.setCategoryId(category.getId());
        Resource created = resourceService.createResource(1L, dto);
        Long id = created.getId();

        resourceService.getResourceById(id);
        Cache cache = cacheManager.getCache(CacheNames.RESOURCE_DETAIL);
        assertNotNull(cache);
        assertNotNull(cache.get(id));

        resourceService.incrementViewCount(id);
        assertNull(cache.get(id), "浏览数变化后必须清除详情缓存");
        assertEquals(1, resourceService.getResourceById(id).getViewCount());

        resourceService.incrementDownloadCount(id);
        assertNull(cache.get(id), "下载数变化后必须清除详情缓存");
        assertEquals(1, resourceService.getResourceById(id).getDownloadCount());
    }
}
