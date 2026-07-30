package com.shiqian.resource.service;

import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.cache.CacheNames;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.mapper.CategoryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CategoryService 缓存集成测试
 */
class CategoryServiceCacheTest extends BaseResourceTest {

    @Autowired
    private CategoryService categoryService;

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
        jdbcTemplate.execute("DELETE FROM t_category");
        jdbcTemplate.execute("ALTER TABLE t_category ALTER COLUMN id RESTART WITH 1");
        Cache cache = cacheManager.getCache(CacheNames.CATEGORY_TREE);
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

        Cache cache = cacheManager.getCache(CacheNames.CATEGORY_TREE);
        assertNotNull(cache, "缓存对象不能为空");
        Cache.ValueWrapper wrapper = cache.get("all");
        assertNotNull(wrapper, "首次查询后缓存必须写入");
        assertNotNull(wrapper.get(), "缓存值不能为空");

        List<Category> second = categoryService.getCategoryTree();
        assertNotNull(second, "二次查询必须返回分类树");
        assertEquals(first.size(), second.size());
    }

    @Test
    void testCacheEvictOnModify() {
        Category category = new Category();
        category.setName("一致性测试分类");
        category.setParentId(0L);
        category.setStatus(1);
        category.setSortOrder(1);
        categoryMapper.insert(category);

        categoryService.getCategoryTree();
        Cache cache = cacheManager.getCache(CacheNames.CATEGORY_TREE);
        assertNotNull(cache);
        assertNotNull(cache.get("all"), "修改前缓存必须存在");

        Category newCategory = new Category();
        newCategory.setName("新增分类");
        newCategory.setParentId(0L);
        newCategory.setStatus(1);
        newCategory.setSortOrder(2);
        categoryService.addCategory(newCategory);

        assertNull(cache.get("all"), "新增分类后缓存必须被清除");

        List<Category> tree = categoryService.getCategoryTree();
        assertEquals(2, tree.size(), "分类树应包含两个根节点");
        assertNotNull(cache.get("all"), "再次查询后缓存应重新写入");

        category.setName("修改后分类");
        categoryService.updateCategory(category);
        assertNull(cache.get("all"), "更新分类后缓存必须被清除");

        categoryService.deleteCategory(newCategory.getId());
        assertNull(cache.get("all"), "删除分类后缓存必须被清除");
    }
}
