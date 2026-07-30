package com.shiqian.resource.service;

import com.shiqian.resource.entity.Resource;

import java.util.Collection;

public interface AuthorEnrichmentService {

    void enrich(Collection<Resource> resources);
}
