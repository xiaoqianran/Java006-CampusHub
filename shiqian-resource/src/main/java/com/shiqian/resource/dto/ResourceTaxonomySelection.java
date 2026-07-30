package com.shiqian.resource.dto;

import java.util.List;

public record ResourceTaxonomySelection(
        List<Long> categoryIds,
        List<String> tagNames) {

    public Long primaryCategoryId() {
        return categoryIds.isEmpty() ? null : categoryIds.get(0);
    }

    public String legacyTags() {
        return tagNames.isEmpty() ? null : String.join(",", tagNames);
    }
}
