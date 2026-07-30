package com.shiqian.resource.storage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiqian.resource.config.ResourceStorageProperties;
import com.shiqian.resource.entity.StoredObject;
import com.shiqian.resource.mapper.StoredObjectMapper;
import com.shiqian.resource.service.StoredObjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageCleanupWorker {

    private final StoredObjectService storedObjectService;
    private final StoredObjectMapper storedObjectMapper;
    private final ObjectStorage objectStorage;
    private final ResourceStorageProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(StorageDeleteEvent event) {
        deleteOne(event.storedObjectId(), event.objectKey(), event.provider());
    }

    @Scheduled(
            fixedDelayString = "${resource.storage.cleanup-interval-ms:3600000}",
            initialDelayString = "${resource.storage.cleanup-initial-delay-ms:600000}")
    public void cleanup() {
        storedObjectService.cleanupExpiredTemporaryObjects();
        List<StoredObject> pending = storedObjectMapper.selectList(
                new QueryWrapper<StoredObject>()
                        .eq("status", "PENDING_DELETE")
                        .orderByAsc("id")
                        .last("LIMIT " + Math.max(1, properties.getCleanupBatchSize())));
        pending.forEach(item -> deleteOne(
                item.getId(), item.getObjectKey(), item.getStorageProvider()));
    }

    private void deleteOne(Long id, String objectKey, String provider) {
        if (!objectStorage.provider().equalsIgnoreCase(provider)) {
            log.warn("待清理对象属于未启用的存储后端: id={}, provider={}", id, provider);
            return;
        }
        try {
            objectStorage.delete(objectKey);
            storedObjectMapper.deleteById(id);
        } catch (IOException error) {
            log.error("存储对象清理失败，稍后重试: id={}, key={}", id, objectKey, error);
        }
    }
}
