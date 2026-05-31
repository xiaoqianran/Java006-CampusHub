package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.mapper.CategoryMapper;
import com.shiqian.resource.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final String CATEGORY_TREE_CACHE_KEY = "category:tree";
    private static final Duration CATEGORY_TREE_TTL = Duration.ofMinutes(30);
    private static final String SPRING_CACHE_KEY = "SimpleKey []";

    private final CategoryMapper categoryMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    @Override
    @CacheEvict(value = "category:tree", allEntries = true)
    public void addCategory(Category category) {
        if (category.getParentId() != null && category.getParentId() != 0) {
            Category parent = categoryMapper.selectById(category.getParentId());
            if (parent == null || parent.getDeleted() == 1) {
                throw new BusinessException("父分类不存在");
            }
        }

        category.setStatus(category.getStatus() != null ? category.getStatus() : 1);
        category.setSortOrder(category.getSortOrder() != null ? category.getSortOrder() : 0);
        // icon (string) and sortOrder are fully supported for create (via DTO + entity fields)
        categoryMapper.insert(category);
        evictCategoryTreeCache();
    }

    @Override
    @CacheEvict(value = "category:tree", allEntries = true)
    public void updateCategory(Category category) {
        Category existing = categoryMapper.selectById(category.getId());
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("分类不存在");
        }

        if (category.getParentId() != null && category.getParentId() != 0
                && !category.getParentId().equals(existing.getParentId())) {
            Category parent = categoryMapper.selectById(category.getParentId());
            if (parent == null || parent.getDeleted() == 1) {
                throw new BusinessException("父分类不存在");
            }
        }

        category.setCreateTime(null);
        // icon (string) and sortOrder fully supported for update (BeanUtils copy from DTO which includes them)
        categoryMapper.updateById(category);
        evictCategoryTreeCache();
    }

    @Override
    @CacheEvict(value = "category:tree", allEntries = true)
    public void deleteCategory(Long id) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("分类不存在");
        }

        QueryWrapper<Category> wrapper = new QueryWrapper<>();
        wrapper.eq("parent_id", id).eq("deleted", 0);
        long childCount = categoryMapper.selectCount(wrapper);
        if (childCount > 0) {
            throw new BusinessException("该分类下存在子分类，无法删除");
        }

        categoryMapper.deleteById(id);
        evictCategoryTreeCache();
    }

    @Override
    public Category getCategoryById(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    public List<Category> getCategoryTree() {
        Cache cache = cacheManager.getCache(CATEGORY_TREE_CACHE_KEY);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(SPRING_CACHE_KEY);
            if (wrapper != null && wrapper.get() instanceof List<?> cachedList) {
                List<Category> tree = cachedList.stream()
                    .filter(Category.class::isInstance)
                    .map(Category.class::cast)
                    .toList();
                redisTemplate.opsForValue().set(CATEGORY_TREE_CACHE_KEY, tree, CATEGORY_TREE_TTL);
                return tree;
            }
        }

        List<Category> tree = loadCategoryTree();
        if (cache != null) {
            cache.put(SPRING_CACHE_KEY, tree);
        }
        redisTemplate.opsForValue().set(CATEGORY_TREE_CACHE_KEY, tree, CATEGORY_TREE_TTL);
        return tree;
    }

    private List<Category> loadCategoryTree() {
        QueryWrapper<Category> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0).eq("status", 1);
        List<Category> allCategories = categoryMapper.selectList(wrapper);

        if (CollectionUtils.isEmpty(allCategories)) {
            return new ArrayList<>();
        }

        Map<Long, List<Category>> childrenMap = allCategories.stream()
                .collect(Collectors.groupingBy(Category::getParentId));

        return buildTree(childrenMap, 0L);
    }

    private void evictCategoryTreeCache() {
        Cache cache = cacheManager.getCache(CATEGORY_TREE_CACHE_KEY);
        if (cache != null) {
            cache.clear();
        }
        redisTemplate.delete(CATEGORY_TREE_CACHE_KEY);
    }

    private List<Category> buildTree(Map<Long, List<Category>> childrenMap, Long parentId) {
        List<Category> children = childrenMap.getOrDefault(parentId, new ArrayList<>());
        children.sort(Comparator.comparingInt(c -> c.getSortOrder() != null ? c.getSortOrder() : 0));

        for (Category child : children) {
            List<Category> grandChildren = buildTree(childrenMap, child.getId());
            child.setChildren(grandChildren);
        }
        return children;
    }

    @Override
    public Page<Category> pageCategories(Page<Category> page) {
        QueryWrapper<Category> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0).orderByAsc("sort_order");
        return categoryMapper.selectPage(page, wrapper);
    }
}
