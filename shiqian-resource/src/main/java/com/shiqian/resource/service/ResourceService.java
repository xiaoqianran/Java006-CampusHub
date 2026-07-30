package com.shiqian.resource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.Resource;

import java.util.List;

public interface ResourceService {

    Resource createResource(Long userId, ResourceCreateDTO dto);

    Resource getResourceById(Long id);

    List<Resource> getPublishedResourcesByIds(List<Long> ids);

    Page<Resource> pageResources(Integer page, Integer size, Long categoryId, String keyword, String sort);

    Page<Resource> pagePublishedResources(Integer page, Integer size, Long categoryId, String keyword, String sort);

    Page<Resource> pageUserResources(Long userId, Integer page, Integer size, String sort);

    Page<Resource> pageRecycleResources(Integer page, Integer size, String keyword);

    void updateResource(Long userId, Long id, ResourceUpdateDTO dto);

    void deleteResource(Long userId, Long id);

    void incrementDownloadCount(Long id);

    void incrementViewCount(Long id);

    void auditResource(Long resourceId, Integer status, Long operatorId);

    void reviewResource(Long resourceId, Integer status, String reason, Long operatorId);

    void resubmitResource(Long userId, Long resourceId);

    void restoreResource(Long id);

    void permanentDeleteResource(Long id);
}
