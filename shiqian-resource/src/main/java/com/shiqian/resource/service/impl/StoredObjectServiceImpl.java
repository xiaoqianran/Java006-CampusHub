package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.config.ResourceStorageProperties;
import com.shiqian.resource.dto.FileUploadVO;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.entity.StoredObject;
import com.shiqian.resource.entity.UserStorageQuota;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.mapper.StoredObjectMapper;
import com.shiqian.resource.mapper.UserStorageQuotaMapper;
import com.shiqian.resource.service.StoredObjectService;
import com.shiqian.resource.storage.FileValidationService;
import com.shiqian.resource.storage.ObjectStorage;
import com.shiqian.resource.storage.StorageDeleteEvent;
import com.shiqian.resource.storage.StoredObjectAccess;
import com.shiqian.resource.storage.ValidatedFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoredObjectServiceImpl implements StoredObjectService {

    private static final String TEMPORARY = "TEMPORARY";
    private static final String BOUND = "BOUND";
    private static final String ARCHIVED = "ARCHIVED";
    private static final String PENDING_DELETE = "PENDING_DELETE";
    private static final Pattern MANAGED_URL = Pattern.compile(
            "^/api/resource/files/object/([0-9a-fA-F-]{36})$");

    private final ObjectStorage objectStorage;
    private final StoredObjectMapper storedObjectMapper;
    private final UserStorageQuotaMapper quotaMapper;
    private final ResourceMapper resourceMapper;
    private final FileValidationService fileValidationService;
    private final ResourceStorageProperties storageProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${resource.upload.max-user-storage:1073741824}")
    private long maxUserStorage;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FileUploadVO> storeFiles(Long ownerId, List<MultipartFile> files) {
        if (ownerId == null || files == null || files.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        List<ValidatedFile> validated = files.stream()
                .map(fileValidationService::validate)
                .toList();
        long batchSize;
        try {
            batchSize = validated.stream()
                    .mapToLong(ValidatedFile::size)
                    .reduce(0L, Math::addExact);
        } catch (ArithmeticException error) {
            throw new BusinessException("上传文件总大小不合法");
        }

        UserStorageQuota quota = lockQuota(ownerId);
        long usedBytes = quota.getUsedBytes() == null ? 0L : quota.getUsedBytes();
        if (batchSize > maxUserStorage - usedBytes) {
            throw new BusinessException("个人文件空间不足，当前上限为"
                    + Math.max(1, maxUserStorage / 1024 / 1024) + "MB");
        }

        List<String> uploadedKeys = new ArrayList<>();
        List<FileUploadVO> result = new ArrayList<>();
        registerRollbackCleanup(uploadedKeys);
        try {
            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                ValidatedFile metadata = validated.get(index);
                String publicId = UUID.randomUUID().toString();
                String objectKey = objectKey(ownerId, publicId, metadata.extension());
                try (var input = file.getInputStream()) {
                    objectStorage.put(
                            objectKey,
                            input,
                            metadata.size(),
                            metadata.mimeType());
                }
                uploadedKeys.add(objectKey);

                StoredObject storedObject = new StoredObject();
                storedObject.setPublicId(publicId);
                storedObject.setOwnerId(ownerId);
                storedObject.setObjectKey(objectKey);
                storedObject.setOriginalName(metadata.originalName());
                storedObject.setStorageProvider(objectStorage.provider());
                storedObject.setBucketName("minio".equalsIgnoreCase(objectStorage.provider())
                        ? storageProperties.getMinio().getBucket()
                        : null);
                storedObject.setFileSize(metadata.size());
                storedObject.setExtension(metadata.extension());
                storedObject.setMimeType(metadata.mimeType());
                storedObject.setAssetKind(metadata.assetKind());
                storedObject.setStatus(TEMPORARY);
                storedObjectMapper.insert(storedObject);
                result.add(toUploadVO(storedObject));
            }
        } catch (IOException | RuntimeException error) {
            uploadedKeys.forEach(this::deleteQuietly);
            if (error instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("文件保存失败，请稍后重试");
        }

        quota.setUsedBytes(usedBytes + batchSize);
        quota.setUpdateTime(LocalDateTime.now());
        quotaMapper.updateById(quota);
        log.info("对象存储上传成功: ownerId={}, count={}, bytes={}, provider={}",
                ownerId, result.size(), batchSize, objectStorage.provider());
        return result;
    }

    @Override
    public StoredObjectAccess open(String publicId, Long requesterId, boolean privileged) {
        StoredObject metadata = requireMetadata(publicId, requesterId, privileged);
        ensureActiveProvider(metadata);
        try {
            return new StoredObjectAccess(metadata, objectStorage.get(metadata.getObjectKey()));
        } catch (IOException error) {
            throw new BusinessException(404, "文件对象不存在或暂时不可用");
        }
    }

    @Override
    public Optional<String> createSignedUrl(
            String publicId,
            Long requesterId,
            boolean privileged,
            boolean inline) {
        StoredObject metadata = requireMetadata(publicId, requesterId, privileged);
        ensureActiveProvider(metadata);
        try {
            return objectStorage.presignedGetUrl(
                    metadata.getObjectKey(),
                    storageProperties.getSignedUrlTtl(),
                    metadata.getOriginalName(),
                    inline);
        } catch (IOException error) {
            throw new BusinessException("文件临时访问地址生成失败");
        }
    }

    @Override
    public StoredObject requireMetadata(String publicId, Long requesterId, boolean privileged) {
        if (!StringUtils.hasText(publicId)) {
            throw new BusinessException(404, "文件不存在");
        }
        StoredObject metadata = storedObjectMapper.selectOne(new QueryWrapper<StoredObject>()
                .eq("public_id", publicId)
                .ne("status", PENDING_DELETE));
        if (metadata == null || !canAccess(metadata, requesterId, privileged)) {
            throw new BusinessException(404, "文件不存在");
        }
        return metadata;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindResourceFiles(Long ownerId, Long resourceId, List<String> fileUrls) {
        Set<String> selectedPublicIds = extractManagedPublicIds(fileUrls);
        List<StoredObject> currentObjects = storedObjectMapper.selectList(
                new QueryWrapper<StoredObject>()
                        .eq("resource_id", resourceId)
                        .in("status", BOUND, ARCHIVED));

        for (String publicId : selectedPublicIds) {
            StoredObject storedObject = storedObjectMapper.selectOne(
                    new QueryWrapper<StoredObject>().eq("public_id", publicId));
            if (storedObject == null || PENDING_DELETE.equals(storedObject.getStatus())) {
                throw new BusinessException("附件已失效，请重新上传");
            }
            if (!ownerId.equals(storedObject.getOwnerId())) {
                throw new BusinessException(403, "不能绑定其他用户上传的附件");
            }
            if (storedObject.getResourceId() != null
                    && !resourceId.equals(storedObject.getResourceId())) {
                throw new BusinessException("附件已绑定到其他资源");
            }
            storedObject.setResourceId(resourceId);
            storedObject.setStatus(BOUND);
            storedObjectMapper.updateById(storedObject);
        }

        currentObjects.stream()
                .filter(item -> !selectedPublicIds.contains(item.getPublicId()))
                .forEach(item -> {
                    item.setStatus(ARCHIVED);
                    storedObjectMapper.updateById(item);
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResourceFiles(Long resourceId) {
        List<StoredObject> objects = storedObjectMapper.selectList(
                new QueryWrapper<StoredObject>()
                        .eq("resource_id", resourceId)
                        .ne("status", PENDING_DELETE));
        markPendingDelete(objects);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredTemporaryObjects() {
        LocalDateTime cutoff = LocalDateTime.now().minus(storageProperties.getTemporaryTtl());
        List<StoredObject> objects = storedObjectMapper.selectList(
                new QueryWrapper<StoredObject>()
                        .eq("status", TEMPORARY)
                        .lt("create_time", cutoff)
                        .orderByAsc("id")
                        .last("LIMIT " + Math.max(1, storageProperties.getCleanupBatchSize())));
        markPendingDelete(objects);
    }

    private void markPendingDelete(List<StoredObject> objects) {
        if (objects == null || objects.isEmpty()) {
            return;
        }
        List<Long> ownerIds = objects.stream()
                .map(StoredObject::getOwnerId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        for (Long ownerId : ownerIds) {
            UserStorageQuota quota = lockQuota(ownerId);
            long releasing = objects.stream()
                    .filter(item -> ownerId.equals(item.getOwnerId()))
                    .mapToLong(item -> item.getFileSize() == null ? 0L : item.getFileSize())
                    .sum();
            quota.setUsedBytes(Math.max(0L, safeUsedBytes(quota) - releasing));
            quota.setUpdateTime(LocalDateTime.now());
            quotaMapper.updateById(quota);
        }
        for (StoredObject item : objects) {
            item.setStatus(PENDING_DELETE);
            storedObjectMapper.updateById(item);
            eventPublisher.publishEvent(new StorageDeleteEvent(
                    item.getId(), item.getObjectKey(), item.getStorageProvider()));
        }
    }

    private UserStorageQuota lockQuota(Long ownerId) {
        quotaMapper.ensureExists(ownerId);
        UserStorageQuota quota = quotaMapper.selectForUpdate(ownerId);
        if (quota == null) {
            throw new BusinessException("用户存储配额初始化失败");
        }
        return quota;
    }

    private long safeUsedBytes(UserStorageQuota quota) {
        return quota.getUsedBytes() == null ? 0L : quota.getUsedBytes();
    }

    private boolean canAccess(StoredObject metadata, Long requesterId, boolean privileged) {
        if (privileged || (requesterId != null && requesterId.equals(metadata.getOwnerId()))) {
            return true;
        }
        if (!BOUND.equals(metadata.getStatus()) || metadata.getResourceId() == null) {
            return false;
        }
        Resource resource = resourceMapper.selectById(metadata.getResourceId());
        return resource != null
                && (resource.getDeleted() == null || resource.getDeleted() == 0)
                && resource.getStatus() != null
                && resource.getStatus() == 1;
    }

    private Set<String> extractManagedPublicIds(List<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String fileUrl : fileUrls) {
            if (!StringUtils.hasText(fileUrl)) continue;
            String path;
            try {
                URI uri = URI.create(fileUrl);
                path = uri.getPath();
            } catch (IllegalArgumentException ignored) {
                path = fileUrl;
            }
            Matcher matcher = MANAGED_URL.matcher(path);
            if (matcher.matches()) {
                ids.add(matcher.group(1).toLowerCase(Locale.ROOT));
            } else if (path.startsWith("/api/resource/files/object/")) {
                throw new BusinessException("附件地址不合法");
            }
        }
        return ids;
    }

    private String objectKey(Long ownerId, String publicId, String extension) {
        return "users/" + ownerId + "/"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"))
                + "/" + publicId + "." + extension;
    }

    private FileUploadVO toUploadVO(StoredObject storedObject) {
        FileUploadVO vo = new FileUploadVO();
        vo.setOriginalName(storedObject.getOriginalName());
        vo.setFileUrl("/api/resource/files/object/" + storedObject.getPublicId());
        vo.setFileSize(storedObject.getFileSize());
        vo.setFileType(storedObject.getExtension());
        vo.setMimeType(storedObject.getMimeType());
        vo.setAssetKind(storedObject.getAssetKind());
        return vo;
    }

    private void ensureActiveProvider(StoredObject metadata) {
        if (!objectStorage.provider().equalsIgnoreCase(metadata.getStorageProvider())) {
            throw new BusinessException("文件所在存储后端当前未启用");
        }
    }

    private void registerRollbackCleanup(List<String> uploadedKeys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    uploadedKeys.forEach(StoredObjectServiceImpl.this::deleteQuietly);
                }
            }
        });
    }

    private void deleteQuietly(String objectKey) {
        try {
            objectStorage.delete(objectKey);
        } catch (IOException error) {
            log.warn("回滚清理存储对象失败: key={}", objectKey, error);
        }
    }
}
