package com.shiqian.resource.service;

import com.shiqian.resource.BaseResourceTest;
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
        jdbcTemplate.execute("DELETE FROM t_resource");
        jdbcTemplate.execute("DELETE FROM t_category");
        jdbcTemplate.execute("ALTER TABLE t_resource ALTER COLUMN id RESTART WITH 1");
        jdbcTemplate.execute("ALTER TABLE t_category ALTER COLUMN id RESTART WITH 1");
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
        dto.setDescription("一致性测试描述");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        Resource created = resourceService.createResource(1L, dto);
        Long id = created.getId();

        resourceService.getResourceById(id);
        Cache cache = cacheManager.getCache("resource:detail");
        assertNotNull(cache);
        assertNotNull(cache.get(id), "更新前缓存必须存在");

        ResourceUpdateDTO updateDTO = new ResourceUpdateDTO();
        updateDTO.setTitle("更新后标题");
        updateDTO.setDescription("更新后描述");
        updateDTO.setCategoryId(category.getId());
        updateDTO.setFileUrl("http://example.com/file.pdf");
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
}
