package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.entity.ResourceAttachment;
import com.shiqian.resource.mapper.ResourceAttachmentMapper;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.service.AuthorEnrichmentService;
import com.shiqian.resource.service.ResourceQueryService;
import com.shiqian.resource.service.ResourceTaxonomyService;
import com.shiqian.resource.service.support.ResourceStatuses;
import com.shiqian.resource.service.support.ResourceSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceQueryServiceImpl implements ResourceQueryService {

    private final ResourceMapper resourceMapper;
    private final ResourceAttachmentMapper resourceAttachmentMapper;
    private final AuthorEnrichmentService authorEnrichmentService;
    private final ResourceTaxonomyService taxonomyService;
    private final ResourceSupport resourceSupport;

    @Override
    public Resource getResourceById(Long id) {
        Resource resource = resourceMapper.selectById(id);
        if (resource == null || resource.getDeleted() == 1) {
            return null;
        }
        List<ResourceAttachment> attachments = resourceAttachmentMapper.selectList(
            new QueryWrapper<ResourceAttachment>().eq("resource_id", id).orderByAsc("sort_order")
        );
        resource.setAttachments(attachments);
        taxonomyService.enrich(List.of(resource));
        authorEnrichmentService.enrich(List.of(resource));
        return resource;
    }

    @Override
    public List<Resource> getPublishedResourcesByIds(List<Long> ids) {
        return getPublishedResourcesByIds(ids, null);
    }

    @Override
    public List<Resource> getPublishedResourcesByIds(List<Long> ids, String contentScene) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        String scene = resourceSupport.normalizeContentScene(contentScene, null);
        Map<Long, Resource> resourcesById = new LinkedHashMap<>();
        resourceMapper.selectBatchIds(ids).stream()
                .filter(resource -> resource.getDeleted() == null || resource.getDeleted() == 0)
                .filter(resource -> resource.getStatus() != null
                        && resource.getStatus() == ResourceStatuses.STATUS_PUBLISHED)
                .filter(resource -> scene == null || scene.equals(resource.getContentScene()))
                .forEach(resource -> resourcesById.put(resource.getId(), resource));

        List<Resource> ordered = ids.stream()
                .map(resourcesById::get)
                .filter(Objects::nonNull)
                .toList();
        authorEnrichmentService.enrich(ordered);
        enrichAttachments(ordered);
        taxonomyService.enrich(ordered);
        return ordered;
    }

    @Override
    public Page<Resource> pageResources(Integer page, Integer size, Long categoryId, String keyword, String sort) {
        return pageResources(page, size, categoryId, keyword, sort, null);
    }

    @Override
    public Page<Resource> pageResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene) {
        return pageResources(page, size, categoryId, keyword, sort, contentScene, null, null);
    }

    @Override
    public Page<Resource> pageResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene,
            Long tagId,
            String tagName) {
        Page<Resource> pageParam = new Page<>(page, size);
        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);

        applyTaxonomyFilters(wrapper, categoryId, tagId, tagName);
        applyContentSceneFilter(wrapper, contentScene);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like("title", keyword)
                    .or().like("summary", keyword)
                    .or().like("description", keyword)  // legacy 兼容旧数据
                    .or().like("tags", keyword)
                    .or().like("content_markdown", keyword));
        }

        applyListOrdering(wrapper, sort, contentScene);
        Page<Resource> result = resourceMapper.selectPage(pageParam, wrapper);
        authorEnrichmentService.enrich(result.getRecords());
        enrichAttachments(result.getRecords());
        taxonomyService.enrich(result.getRecords());
        return result;
    }

    @Override
    public Page<Resource> pagePublishedResources(Integer page, Integer size, Long categoryId, String keyword, String sort) {
        return pagePublishedResources(page, size, categoryId, keyword, sort, null);
    }

    @Override
    public Page<Resource> pagePublishedResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene) {
        return pagePublishedResources(page, size, categoryId, keyword, sort, contentScene, null, null);
    }

    @Override
    public Page<Resource> pagePublishedResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene,
            Long tagId,
            String tagName) {
        Page<Resource> pageParam = new Page<>(page, size);
        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        wrapper.eq("status", 1);

        applyTaxonomyFilters(wrapper, categoryId, tagId, tagName);
        applyContentSceneFilter(wrapper, contentScene);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like("title", keyword)
                    .or().like("summary", keyword)
                    .or().like("description", keyword)
                    .or().like("tags", keyword)
                    .or().like("content_markdown", keyword));
        }

        applyListOrdering(wrapper, sort, contentScene);
        Page<Resource> result = resourceMapper.selectPage(pageParam, wrapper);
        authorEnrichmentService.enrich(result.getRecords());
        enrichAttachments(result.getRecords());
        taxonomyService.enrich(result.getRecords());
        return result;
    }

    @Override
    public Page<Resource> pageRecycleResources(Integer page, Integer size, String keyword) {
        Page<Resource> pageParam = new Page<>(page, size);
        Page<Resource> result = resourceMapper.selectRecyclePage(pageParam, keyword);
        authorEnrichmentService.enrich(result.getRecords());
        return result;
    }

    @Override
    public Page<Resource> pageUserResources(Long userId, Integer page, Integer size, String sort) {
        Page<Resource> pageParam = new Page<>(page, size);
        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        wrapper.eq("user_id", userId);

        if ("hottest".equals(sort)) {
            wrapper.orderByDesc("download_count");
            wrapper.orderByDesc("view_count");
        } else {
            wrapper.orderByDesc("create_time");
        }
        Page<Resource> result = resourceMapper.selectPage(pageParam, wrapper);
        authorEnrichmentService.enrich(result.getRecords());
        enrichAttachments(result.getRecords());
        taxonomyService.enrich(result.getRecords());
        return result;
    }

    private void applyContentSceneFilter(QueryWrapper<Resource> wrapper, String contentScene) {
        String scene = resourceSupport.normalizeContentScene(contentScene, null);
        if (scene != null) {
            wrapper.eq("content_scene", scene);
        }
    }

    private void applyTaxonomyFilters(
            QueryWrapper<Resource> wrapper,
            Long categoryId,
            Long tagId,
            String tagName) {
        if (categoryId != null) {
            wrapper.and(group -> group
                    .eq("category_id", categoryId)
                    .or()
                    .exists("""
                            SELECT 1
                            FROM t_resource_category rc
                            WHERE rc.resource_id = t_resource.id
                              AND rc.category_id = {0}
                            """, categoryId));
        }
        if (tagId != null) {
            wrapper.exists("""
                    SELECT 1
                    FROM t_resource_tag rt
                    WHERE rt.resource_id = t_resource.id
                      AND rt.tag_id = {0}
                    """, tagId);
        }
        if (StringUtils.hasText(tagName)) {
            wrapper.and(group -> group
                    .like("tags", tagName.trim())
                    .or()
                    .exists("""
                            SELECT 1
                            FROM t_resource_tag rt
                            JOIN t_tag t ON t.id = rt.tag_id
                            WHERE rt.resource_id = t_resource.id
                              AND t.deleted = 0
                              AND t.status = 1
                              AND t.name = {0}
                            """, tagName.trim()));
        }
    }

    /**
     * 列表排序：图片频道优先有封面，避免“只有提示词”的条目霸占首页。
     */
    private void applyListOrdering(QueryWrapper<Resource> wrapper, String sort, String contentScene) {
        String scene = resourceSupport.normalizeContentScene(contentScene, null);
        if ("GALLERY".equals(scene)) {
            if ("hottest".equals(sort)) {
                wrapper.last("ORDER BY (file_url IS NOT NULL AND TRIM(file_url) <> '') DESC, "
                        + "download_count DESC, view_count DESC, id DESC");
            } else {
                wrapper.last("ORDER BY (file_url IS NOT NULL AND TRIM(file_url) <> '') DESC, "
                        + "create_time DESC, id DESC");
            }
            return;
        }
        if ("hottest".equals(sort)) {
            wrapper.orderByDesc("download_count");
            wrapper.orderByDesc("view_count");
        } else {
            wrapper.orderByDesc("create_time");
        }
    }

    private void enrichAttachments(List<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            return;
        }
        List<Long> resourceIds = resources.stream()
                .map(Resource::getId)
                .filter(Objects::nonNull)
                .toList();
        if (resourceIds.isEmpty()) {
            return;
        }
        Map<Long, List<ResourceAttachment>> grouped = resourceAttachmentMapper.selectList(
                        new QueryWrapper<ResourceAttachment>()
                                .in("resource_id", resourceIds)
                                .orderByAsc("sort_order"))
                .stream()
                .collect(Collectors.groupingBy(
                        ResourceAttachment::getResourceId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        resources.forEach(resource ->
                resource.setAttachments(grouped.getOrDefault(resource.getId(), Collections.emptyList())));
    }
}
