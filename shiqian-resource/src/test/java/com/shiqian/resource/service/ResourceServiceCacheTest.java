package com.shiqian.resource.service;

import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.CategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ResourceService 缓存集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
class ResourceServiceCacheTest {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        Cache cache = cacheManager.getCache("resource:detail");
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
        dto.setDescription("缓存测试描述");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        Resource created = resourceService.createResource(1L, dto);
        assertNotNull(created.getId(), "资源创建后 ID 不能为空");
        Long id = created.getId();

        Resource first = resourceService.getResourceById(id);
        assertNotNull(first, "首次查询必须返回资源对象");
        assertEquals("缓存测试资源", first.getTitle());

        Cache cache = cacheManager.getCache("resource:detail");
        assertNotNull(cache, "缓存对象不能为空");
        Cache.ValueWrapper wrapper = cache.get(id);
        assertNotNull(wrapper, "首次查询后缓存必须写入");
        assertNotNull(wrapper.get(), "缓存值不能为空");

        Resource second = resourceService.getResourceById(id);
        assertNotNull(second, "二次查询必须返回资源对象");
        assertEquals(first.getTitle(), second.getTitle());
    }
}
