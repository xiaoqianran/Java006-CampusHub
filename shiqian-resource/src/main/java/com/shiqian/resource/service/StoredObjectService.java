package com.shiqian.resource.service;

import com.shiqian.resource.dto.FileUploadVO;
import com.shiqian.resource.entity.StoredObject;
import com.shiqian.resource.storage.StoredObjectAccess;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface StoredObjectService {

    List<FileUploadVO> storeFiles(Long ownerId, List<MultipartFile> files);

    StoredObjectAccess open(String publicId, Long requesterId, boolean privileged);

    Optional<String> createSignedUrl(
            String publicId,
            Long requesterId,
            boolean privileged,
            boolean inline);

    StoredObject requireMetadata(String publicId, Long requesterId, boolean privileged);

    void bindResourceFiles(Long ownerId, Long resourceId, List<String> fileUrls);

    void deleteResourceFiles(Long resourceId);

    void cleanupExpiredTemporaryObjects();
}
