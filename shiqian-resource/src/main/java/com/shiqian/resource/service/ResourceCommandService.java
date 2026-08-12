package com.shiqian.resource.service;

import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.Resource;

/**
 * Write-side resource operations: create/update/delete/restore and related side effects.
 */
public interface ResourceCommandService {

    Resource createResource(Long userId, ResourceCreateDTO dto);

    void updateResource(Long userId, Long id, ResourceUpdateDTO dto);

    void deleteResource(Long userId, Long id);

    void incrementDownloadCount(Long id);

    void incrementViewCount(Long id);

    void resubmitResource(Long userId, Long resourceId);

    void restoreResource(Long id);

    void permanentDeleteResource(Long id);
}
