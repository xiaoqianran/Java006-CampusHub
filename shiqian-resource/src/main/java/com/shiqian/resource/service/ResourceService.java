package com.shiqian.resource.service;

import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.entity.Resource;

public interface ResourceService {

    Resource createResource(Long userId, ResourceCreateDTO dto);

    Resource getResourceById(Long id);
}
