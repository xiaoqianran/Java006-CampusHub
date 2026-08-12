package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.cache.CacheNames;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.service.ResourceCommandService;
import com.shiqian.resource.service.ResourceQueryService;
import com.shiqian.resource.service.ResourceReviewService;
import com.shiqian.resource.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Thin facade over command / review / query collaborators.
 * Cache, security, and transaction annotations stay here so existing callers
 * ({@code ResourceService}) keep the same Spring AOP boundaries.
 */
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final ResourceCommandService resourceCommandService;
    private final ResourceReviewService resourceReviewService;
    private final ResourceQueryService resourceQueryService;

    @Override
    @CacheEvict(
            cacheNames = CacheNames.RESOURCE_DETAIL,
            key = "#result.id")
    @Transactional(rollbackFor = Exception.class)
    public Resource createResource(Long userId, ResourceCreateDTO dto) {
        return resourceCommandService.createResource(userId, dto);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id", sync = true)
    public Resource getResourceById(Long id) {
        return resourceQueryService.getResourceById(id);
    }

    @Override
    public List<Resource> getPublishedResourcesByIds(List<Long> ids) {
        return resourceQueryService.getPublishedResourcesByIds(ids);
    }

    @Override
    public List<Resource> getPublishedResourcesByIds(List<Long> ids, String contentScene) {
        return resourceQueryService.getPublishedResourcesByIds(ids, contentScene);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id")
    @Transactional(rollbackFor = Exception.class)
    public void updateResource(Long userId, Long id, ResourceUpdateDTO dto) {
        resourceCommandService.updateResource(userId, id, dto);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id")
    @Transactional(rollbackFor = Exception.class)
    public void deleteResource(Long userId, Long id) {
        resourceCommandService.deleteResource(userId, id);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id")
    @Transactional(rollbackFor = Exception.class)
    public void incrementDownloadCount(Long id) {
        resourceCommandService.incrementDownloadCount(id);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id")
    @Transactional(rollbackFor = Exception.class)
    public void incrementViewCount(Long id) {
        resourceCommandService.incrementViewCount(id);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#resourceId")
    @Transactional(rollbackFor = Exception.class)
    public void auditResource(Long resourceId, Integer status, Long operatorId) {
        resourceReviewService.auditResource(resourceId, status, operatorId);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#resourceId")
    @Transactional(rollbackFor = Exception.class)
    public void reviewResource(Long resourceId, Integer status, String reason, Long operatorId) {
        resourceReviewService.reviewResource(resourceId, status, reason, operatorId);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#resourceId")
    @Transactional(rollbackFor = Exception.class)
    public void resubmitResource(Long userId, Long resourceId) {
        resourceCommandService.resubmitResource(userId, resourceId);
    }

    @Override
    public Page<Resource> pageResources(Integer page, Integer size, Long categoryId, String keyword, String sort) {
        return resourceQueryService.pageResources(page, size, categoryId, keyword, sort);
    }

    @Override
    public Page<Resource> pageResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene) {
        return resourceQueryService.pageResources(page, size, categoryId, keyword, sort, contentScene);
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
        return resourceQueryService.pageResources(
                page, size, categoryId, keyword, sort, contentScene, tagId, tagName);
    }

    @Override
    public Page<Resource> pagePublishedResources(Integer page, Integer size, Long categoryId, String keyword, String sort) {
        return resourceQueryService.pagePublishedResources(page, size, categoryId, keyword, sort);
    }

    @Override
    public Page<Resource> pagePublishedResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene) {
        return resourceQueryService.pagePublishedResources(page, size, categoryId, keyword, sort, contentScene);
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
        return resourceQueryService.pagePublishedResources(
                page, size, categoryId, keyword, sort, contentScene, tagId, tagName);
    }

    @Override
    public Page<Resource> pageRecycleResources(Integer page, Integer size, String keyword) {
        return resourceQueryService.pageRecycleResources(page, size, keyword);
    }

    @Override
    public Page<Resource> pageUserResources(Long userId, Integer page, Integer size, String sort) {
        return resourceQueryService.pageUserResources(userId, page, size, sort);
    }

    @Override
    @PreAuthorize("hasAuthority('resource:audit')")
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id")
    @Transactional(rollbackFor = Exception.class)
    public void restoreResource(Long id) {
        resourceCommandService.restoreResource(id);
    }

    @Override
    @PreAuthorize("hasAuthority('resource:audit')")
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id")
    @Transactional(rollbackFor = Exception.class)
    public void permanentDeleteResource(Long id) {
        resourceCommandService.permanentDeleteResource(id);
    }
}
