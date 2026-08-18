package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.cache.CacheNames;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.mapper.CategoryMapper;
import com.shiqian.resource.outbox.OutboxEventType;
import com.shiqian.resource.outbox.OutboxService;
import com.shiqian.resource.outbox.ResourceEventPayload;
import com.shiqian.resource.service.CategoryService;
import com.shiqian.resource.service.ResourceTaxonomyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final ResourceTaxonomyService taxonomyService;
    private final OutboxService outboxService;

    @Override
    @CacheEvict(cacheNames = CacheNames.CATEGORY_TREE, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public void addCategory(Category category) {
        validateParentAssignment(null, category.getParentId());

        category.setStatus(category.getStatus() != null ? category.getStatus() : 1);
        category.setSortOrder(category.getSortOrder() != null ? category.getSortOrder() : 0);
        // icon (string) and sortOrder are fully supported for create (via DTO + entity fields)
        categoryMapper.insert(category);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CATEGORY_TREE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(Category category) {
        Category existing = categoryMapper.selectById(category.getId());
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("分类不存在");
        }

        if (category.getParentId() != null
                && !category.getParentId().equals(existing.getParentId())) {
            validateParentAssignment(category.getId(), category.getParentId());
        }

        category.setCreateTime(null);
        // icon (string) and sortOrder fully supported for update (BeanUtils copy from DTO which includes them)
        categoryMapper.updateById(category);
        taxonomyService.resourceIdsByCategory(category.getId()).forEach(resourceId ->
                outboxService.append(
                        OutboxEventType.RESOURCE_UPDATED,
                        resourceId,
                        ResourceEventPayload.resource(resourceId)));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CATEGORY_TREE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
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

        List<Long> affected = taxonomyService.removeCategoryRelations(id);
        categoryMapper.deleteById(id);
        affected.forEach(resourceId -> outboxService.append(
                OutboxEventType.RESOURCE_UPDATED,
                resourceId,
                ResourceEventPayload.resource(resourceId)));
    }

    @Override
    public Category getCategoryById(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    @Cacheable(
            cacheNames = CacheNames.CATEGORY_TREE,
            key = CacheNames.CATEGORY_TREE_KEY,
            sync = true)
    public List<Category> getCategoryTree() {
        return loadCategoryTree();
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

        return buildTree(childrenMap, 0L, new HashSet<>());
    }

    private void validateParentAssignment(Long categoryId, Long parentId) {
        if (parentId == null || parentId == 0) {
            return;
        }
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && cursor != 0) {
            if (categoryId != null && categoryId.equals(cursor)) {
                throw new BusinessException("不能将分类移动到自身或其子分类下");
            }
            if (!visited.add(cursor)) {
                throw new BusinessException("分类层级存在循环，请先修复父子关系");
            }
            Category parent = categoryMapper.selectById(cursor);
            if (parent == null || parent.getDeleted() == 1) {
                throw new BusinessException("父分类不存在");
            }
            cursor = parent.getParentId();
        }
    }

    private List<Category> buildTree(
            Map<Long, List<Category>> childrenMap,
            Long parentId,
            Set<Long> visited) {
        List<Category> children = childrenMap.getOrDefault(parentId, new ArrayList<>());
        children.sort(Comparator.comparingInt(c -> c.getSortOrder() != null ? c.getSortOrder() : 0));

        List<Category> safeChildren = new ArrayList<>();
        for (Category child : children) {
            if (child.getId() == null || !visited.add(child.getId())) {
                log.warn("检测到分类树重复/循环节点，已跳过: parentId={}, childId={}", parentId, child.getId());
                continue;
            }
            List<Category> grandChildren = buildTree(childrenMap, child.getId(), visited);
            child.setChildren(grandChildren);
            safeChildren.add(child);
        }
        return safeChildren;
    }

    @Override
    public Page<Category> pageCategories(Page<Category> page) {
        QueryWrapper<Category> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0).orderByAsc("sort_order");
        return categoryMapper.selectPage(page, wrapper);
    }
}
