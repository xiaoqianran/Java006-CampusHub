package com.shiqian.resource.service.support;

import java.util.Set;

/**
 * Shared resource lifecycle / content-scene constants for command, review, and query services.
 */
public final class ResourceStatuses {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_NEEDS_CHANGES = 2;
    public static final int STATUS_REJECTED = 3;
    public static final int STATUS_OFFLINE = 4;

    public static final Set<String> CONTENT_SCENES = Set.of("BLOG", "GALLERY", "SHARE");

    private ResourceStatuses() {
    }
}
