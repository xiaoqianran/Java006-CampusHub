package com.shiqian.resource.service;

import com.shiqian.resource.dto.ResourceTaxonomySelection;
import com.shiqian.resource.entity.Resource;

import java.util.List;

public interface ResourceTaxonomyService {

    ResourceTaxonomySelection normalize(
            Long legacyCategoryId,
            List<Long> categoryIds,
            String legacyTags,
            List<String> tagNames);

    void sync(Long resourceId, ResourceTaxonomySelection selection);

    void enrich(List<Resource> resources);

    ResourceTaxonomySelection selectionOf(Resource resource);

    List<Long> removeCategoryRelations(Long categoryId);

    List<Long> resourceIdsByCategory(Long categoryId);

    List<Long> removeTagRelations(Long tagId);

    void removeResourceRelations(Long resourceId);
}
