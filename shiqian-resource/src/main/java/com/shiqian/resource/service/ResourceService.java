package com.shiqian.resource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.Resource;

public interface ResourceService {

    Resource createResource(Long userId, ResourceCreateDTO dto);

    Resource getResourceById(Long id);

    Page<Resource> pageResources(Integer page, Integer size, Long categoryId, String keyword);

    void updateResource(Long userId, Long id, ResourceUpdateDTO dto);

    void deleteResource(Long userId, Long id);
}
