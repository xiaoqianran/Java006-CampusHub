package com.shiqian.resource.service;

import com.shiqian.resource.dto.ResourceVersionVO;
import com.shiqian.resource.entity.Resource;

import java.util.List;

public interface ResourceVersionService {

    void ensureInitialSnapshot(Resource resource);

    void recordSnapshot(Resource resource, Long actorId, String changeDescription);

    List<ResourceVersionVO> listVersions(Long actorId, Long resourceId);

    ResourceVersionVO getVersion(Long actorId, Long resourceId, Integer versionNumber);

    Resource rollback(
            Long actorId,
            Long resourceId,
            Integer versionNumber,
            String changeDescription);

    void deleteVersions(Long resourceId);
}
