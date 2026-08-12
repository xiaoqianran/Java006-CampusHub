package com.shiqian.resource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.entity.Resource;

import java.util.List;

/**
 * Read-side resource operations: detail, published lookups, and paging lists.
 */
public interface ResourceQueryService {

    Resource getResourceById(Long id);

    List<Resource> getPublishedResourcesByIds(List<Long> ids);

    List<Resource> getPublishedResourcesByIds(List<Long> ids, String contentScene);

    Page<Resource> pageResources(Integer page, Integer size, Long categoryId, String keyword, String sort);

    Page<Resource> pageResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene);

    Page<Resource> pageResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene,
            Long tagId,
            String tagName);

    Page<Resource> pagePublishedResources(Integer page, Integer size, Long categoryId, String keyword, String sort);

    Page<Resource> pagePublishedResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene);

    Page<Resource> pagePublishedResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene,
            Long tagId,
            String tagName);

    Page<Resource> pageUserResources(Long userId, Integer page, Integer size, String sort);

    Page<Resource> pageRecycleResources(Integer page, Integer size, String keyword);
}
