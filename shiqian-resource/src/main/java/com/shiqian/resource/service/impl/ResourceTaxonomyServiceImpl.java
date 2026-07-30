package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.dto.ResourceTaxonomySelection;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.entity.ResourceCategory;
import com.shiqian.resource.entity.ResourceTag;
import com.shiqian.resource.entity.Tag;
import com.shiqian.resource.mapper.CategoryMapper;
import com.shiqian.resource.mapper.ResourceCategoryMapper;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.mapper.ResourceTagMapper;
import com.shiqian.resource.mapper.TagMapper;
import com.shiqian.resource.service.ResourceTaxonomyService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceTaxonomyServiceImpl implements ResourceTaxonomyService {

    private static final int MAX_CATEGORIES = 10;
    private static final int MAX_TAGS = 20;

    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ResourceCategoryMapper resourceCategoryMapper;
    private final ResourceTagMapper resourceTagMapper;
    private final ResourceMapper resourceMapper;

    @Override
    public ResourceTaxonomySelection normalize(
            Long legacyCategoryId,
            List<Long> categoryIds,
            String legacyTags,
            List<String> tagNames) {
        LinkedHashSet<Long> normalizedCategoryIds = new LinkedHashSet<>();
        if (categoryIds != null) {
            categoryIds.stream().filter(Objects::nonNull).forEach(normalizedCategoryIds::add);
        } else if (legacyCategoryId != null) {
            normalizedCategoryIds.add(legacyCategoryId);
        }
        if (normalizedCategoryIds.size() > MAX_CATEGORIES) {
            throw new BusinessException("每个资源最多选择10个分类");
        }
        validateCategories(normalizedCategoryIds);

        List<String> sourceTags = tagNames != null
                ? tagNames
                : splitLegacyTags(legacyTags);
        Map<String, String> normalizedTags = new LinkedHashMap<>();
        sourceTags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(tag -> {
                    if (tag.length() > 50) {
                        throw new BusinessException("单个标签最多50个字符");
                    }
                    normalizedTags.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
                });
        if (normalizedTags.size() > MAX_TAGS) {
            throw new BusinessException("每个资源最多添加20个标签");
        }
        return new ResourceTaxonomySelection(
                List.copyOf(normalizedCategoryIds),
                List.copyOf(normalizedTags.values()));
    }

    @Override
    public void sync(Long resourceId, ResourceTaxonomySelection selection) {
        removeResourceRelations(resourceId);
        for (Long categoryId : selection.categoryIds()) {
            ResourceCategory relation = new ResourceCategory();
            relation.setResourceId(resourceId);
            relation.setCategoryId(categoryId);
            resourceCategoryMapper.insert(relation);
        }
        for (String tagName : selection.tagNames()) {
            Tag tag = findOrCreateTag(tagName);
            ResourceTag relation = new ResourceTag();
            relation.setResourceId(resourceId);
            relation.setTagId(tag.getId());
            resourceTagMapper.insert(relation);
        }
    }

    @Override
    public void enrich(List<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            return;
        }
        List<Long> resourceIds = resources.stream()
                .map(Resource::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (resourceIds.isEmpty()) {
            return;
        }

        List<ResourceCategory> categoryRelations = resourceCategoryMapper.selectList(
                new QueryWrapper<ResourceCategory>()
                        .in("resource_id", resourceIds)
                        .orderByAsc("create_time"));
        List<ResourceTag> tagRelations = resourceTagMapper.selectList(
                new QueryWrapper<ResourceTag>()
                        .in("resource_id", resourceIds)
                        .orderByAsc("create_time"));

        Set<Long> categoryIds = categoryRelations.stream()
                .map(ResourceCategory::getCategoryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        resources.stream()
                .map(Resource::getCategoryId)
                .filter(Objects::nonNull)
                .forEach(categoryIds::add);
        Map<Long, Category> categories = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .filter(category -> category.getDeleted() == null || category.getDeleted() == 0)
                        .collect(Collectors.toMap(Category::getId, Function.identity()));

        Set<Long> tagIds = tagRelations.stream()
                .map(ResourceTag::getTagId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Tag> tags = tagIds.isEmpty()
                ? Collections.emptyMap()
                : tagMapper.selectBatchIds(tagIds).stream()
                        .filter(tag -> tag.getDeleted() == null || tag.getDeleted() == 0)
                        .collect(Collectors.toMap(Tag::getId, Function.identity()));

        Map<Long, List<Long>> categoryIdsByResource = categoryRelations.stream()
                .collect(Collectors.groupingBy(
                        ResourceCategory::getResourceId,
                        LinkedHashMap::new,
                        Collectors.mapping(ResourceCategory::getCategoryId, Collectors.toList())));
        Map<Long, List<Long>> tagIdsByResource = tagRelations.stream()
                .collect(Collectors.groupingBy(
                        ResourceTag::getResourceId,
                        LinkedHashMap::new,
                        Collectors.mapping(ResourceTag::getTagId, Collectors.toList())));

        for (Resource resource : resources) {
            List<Long> selectedCategoryIds = new ArrayList<>(
                    categoryIdsByResource.getOrDefault(resource.getId(), Collections.emptyList()));
            if (selectedCategoryIds.isEmpty() && resource.getCategoryId() != null) {
                selectedCategoryIds.add(resource.getCategoryId());
            }
            resource.setCategoryIds(selectedCategoryIds);
            resource.setCategoryNames(selectedCategoryIds.stream()
                    .map(categories::get)
                    .filter(Objects::nonNull)
                    .map(Category::getName)
                    .toList());

            List<Long> selectedTagIds = tagIdsByResource.getOrDefault(
                    resource.getId(), Collections.emptyList());
            if (selectedTagIds.isEmpty()) {
                resource.setTagIds(Collections.emptyList());
                resource.setTagNames(splitLegacyTags(resource.getTags()));
            } else {
                resource.setTagIds(selectedTagIds.stream().filter(tags::containsKey).toList());
                resource.setTagNames(selectedTagIds.stream()
                        .map(tags::get)
                        .filter(Objects::nonNull)
                        .map(Tag::getName)
                        .toList());
            }
        }
    }

    @Override
    public ResourceTaxonomySelection selectionOf(Resource resource) {
        enrich(List.of(resource));
        return new ResourceTaxonomySelection(
                resource.getCategoryIds() == null ? List.of() : resource.getCategoryIds(),
                resource.getTagNames() == null ? List.of() : resource.getTagNames());
    }

    @Override
    public List<Long> removeCategoryRelations(Long categoryId) {
        List<Long> affected = resourceCategoryMapper.selectList(
                        new QueryWrapper<ResourceCategory>().eq("category_id", categoryId))
                .stream()
                .map(ResourceCategory::getResourceId)
                .distinct()
                .toList();
        resourceCategoryMapper.delete(
                new QueryWrapper<ResourceCategory>().eq("category_id", categoryId));
        for (Long resourceId : affected) {
            ResourceCategory first = resourceCategoryMapper.selectOne(
                    new QueryWrapper<ResourceCategory>()
                            .eq("resource_id", resourceId)
                            .orderByAsc("create_time")
                            .last("LIMIT 1"));
            resourceMapper.update(null, new UpdateWrapper<Resource>()
                    .eq("id", resourceId)
                    .set("category_id", first == null ? null : first.getCategoryId()));
        }
        return affected;
    }

    @Override
    public List<Long> resourceIdsByCategory(Long categoryId) {
        return resourceCategoryMapper.selectList(
                        new QueryWrapper<ResourceCategory>().eq("category_id", categoryId))
                .stream()
                .map(ResourceCategory::getResourceId)
                .distinct()
                .toList();
    }

    @Override
    public List<Long> removeTagRelations(Long tagId) {
        List<Long> affected = resourceTagMapper.selectList(
                        new QueryWrapper<ResourceTag>().eq("tag_id", tagId))
                .stream()
                .map(ResourceTag::getResourceId)
                .distinct()
                .toList();
        resourceTagMapper.delete(new QueryWrapper<ResourceTag>().eq("tag_id", tagId));
        for (Long resourceId : affected) {
            List<Long> remainingTagIds = resourceTagMapper.selectList(
                            new QueryWrapper<ResourceTag>()
                                    .eq("resource_id", resourceId)
                                    .orderByAsc("create_time"))
                    .stream()
                    .map(ResourceTag::getTagId)
                    .toList();
            List<String> names = remainingTagIds.isEmpty()
                    ? List.of()
                    : tagMapper.selectBatchIds(remainingTagIds).stream()
                            .filter(tag -> tag.getDeleted() == null || tag.getDeleted() == 0)
                            .map(Tag::getName)
                            .toList();
            resourceMapper.update(null, new UpdateWrapper<Resource>()
                    .eq("id", resourceId)
                    .set("tags", names.isEmpty() ? null : String.join(",", names)));
        }
        return affected;
    }

    @Override
    public void removeResourceRelations(Long resourceId) {
        resourceCategoryMapper.delete(
                new QueryWrapper<ResourceCategory>().eq("resource_id", resourceId));
        resourceTagMapper.delete(
                new QueryWrapper<ResourceTag>().eq("resource_id", resourceId));
    }

    private void validateCategories(Set<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return;
        }
        long count = categoryMapper.selectCount(new QueryWrapper<Category>()
                .in("id", categoryIds)
                .eq("status", 1)
                .eq("deleted", 0));
        if (count != categoryIds.size()) {
            // 保持旧客户端依赖的错误文案。
            throw new BusinessException("分类不存在");
        }
    }

    private Tag findOrCreateTag(String name) {
        Tag existing = tagMapper.selectByNameIncludingDeleted(name);
        if (existing != null) {
            if (existing.getDeleted() != null && existing.getDeleted() == 1) {
                tagMapper.restoreById(existing.getId());
            }
            return existing;
        }
        Tag tag = new Tag();
        tag.setName(name);
        tag.setStatus(1);
        try {
            tagMapper.insert(tag);
            return tag;
        } catch (DuplicateKeyException ignored) {
            return tagMapper.selectByNameIncludingDeleted(name);
        }
    }

    private List<String> splitLegacyTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return List.of();
        }
        return java.util.Arrays.stream(tags.split("[,，]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(MAX_TAGS)
                .toList();
    }
}
