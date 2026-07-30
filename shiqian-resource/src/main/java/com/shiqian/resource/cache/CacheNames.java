package com.shiqian.resource.cache;

/**
 * 资源服务缓存命名规范。
 *
 * 实际 Redis Key：campushub:v1:cache:{cacheName}::{key}
 */
public final class CacheNames {

    public static final String KEY_PREFIX = "campushub:v1:cache:";
    public static final String RESOURCE_DETAIL = "resource-detail";
    public static final String CATEGORY_TREE = "category-tree";
    public static final String CATEGORY_TREE_KEY = "'all'";

    private CacheNames() {
    }
}
