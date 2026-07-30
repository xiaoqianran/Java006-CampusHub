package com.shiqian.resource.service;

import com.shiqian.resource.document.ResourceDocument;
import org.springframework.data.domain.Page;

public interface ResourceSearchService {

    Page<ResourceDocument> search(String keyword, Integer page, Integer size, String sort);

    Page<ResourceDocument> search(String keyword, Integer page, Integer size, String sort, String contentScene);
}
