package com.shiqian.resource.service;

import com.shiqian.resource.entity.Category;
import com.shiqian.resource.mapper.CategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CategoryService 缓存集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
class CategoryServiceCacheTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        Cache cache = cacheManager.getCache("category:tree");
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void testGetCategoryTreeCacheable() {
        Category parent = new Category();
        parent.setName("缓存父分类");
        parent.setParentId(0L);
        parent.setStatus(1);
        parent.setSortOrder(1);
        categoryMapper.insert(parent);

        Category child = new Category();
        child.setName("缓存子分类");
        child.setParentId(parent.getId());
        child.setStatus(1);
        child.setSortOrder(2);
        categoryMapper.insert(child);

        List<Category> first = categoryService.getCategoryTree();
        assertNotNull(first, "首次查询必须返回分类树");
        assertFalse(first.isEmpty(), "分类树不能为空");

        Cache cache = cacheManager.getCache("category:tree");
        assertNotNull(cache, "缓存对象不能为空");
        Cache.ValueWrapper wrapper = cache.get("SimpleKey []");
        assertNotNull(wrapper, "首次查询后缓存必须写入");
        assertNotNull(wrapper.get(), "缓存值不能为空");

        List<Category> second = categoryService.getCategoryTree();
        assertNotNull(second, "二次查询必须返回分类树");
        assertEquals(first.size(), second.size());
    }

    private void assertEquals(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
