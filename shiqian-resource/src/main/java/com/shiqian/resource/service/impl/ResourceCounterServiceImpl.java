package com.shiqian.resource.service.impl;

import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.cache.CacheNames;
import com.shiqian.resource.config.ResourceCounterProperties;
import com.shiqian.resource.entity.CounterFlushBatch;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.CounterFlushBatchMapper;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.outbox.OutboxEventType;
import com.shiqian.resource.outbox.OutboxService;
import com.shiqian.resource.outbox.ResourceEventPayload;
import com.shiqian.resource.service.ResourceCounterService;
import com.shiqian.resource.service.ResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceCounterServiceImpl implements ResourceCounterService {

    private static final String KEY_PREFIX = "campushub:v1:counter:";
    private static final String ACTIVE_VIEW = KEY_PREFIX + "view:active";
    private static final String ACTIVE_DOWNLOAD = KEY_PREFIX + "download:active";
    private static final String BATCH_PATTERN = KEY_PREFIX + "*:batch:*";
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('EXISTS', KEYS[1]) == 1 then
                      redis.call('RENAME', KEYS[1], KEYS[2])
                      return 1
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ResourceMapper resourceMapper;
    private final CounterFlushBatchMapper batchMapper;
    private final OutboxService outboxService;
    private final ResourceService resourceService;
    private final ResourceCounterProperties properties;
    private final PlatformTransactionManager transactionManager;
    private final CacheManager cacheManager;

    @Override
    public boolean recordView(Long resourceId, Long userId, String clientIp) {
        Resource resource = requirePublished(resourceId);
        if (!properties.isEnabled()) {
            resourceService.incrementViewCount(resource.getId());
            return true;
        }
        String identity = userId != null
                ? "user:" + userId
                : "ip:" + (StringUtils.hasText(clientIp) ? clientIp : "unknown");
        String dedupKey = KEY_PREFIX + "view:dedup:" + resourceId + ":" + hash(identity);
        try {
            Boolean first = redisTemplate.opsForValue().setIfAbsent(
                    dedupKey,
                    "1",
                    properties.getViewDedupTtl().toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!Boolean.TRUE.equals(first)) return false;
            redisTemplate.opsForHash().increment(ACTIVE_VIEW, String.valueOf(resourceId), 1L);
            return true;
        } catch (RuntimeException error) {
            log.error("Redis浏览计数聚合不可用，降级为数据库计数: resourceId={}", resourceId, error);
            resourceService.incrementViewCount(resourceId);
            return true;
        }
    }

    @Override
    public void recordDownload(Long resourceId) {
        if (!properties.isEnabled()) {
            resourceService.incrementDownloadCount(resourceId);
            return;
        }
        requirePublished(resourceId);
        try {
            redisTemplate.opsForHash().increment(
                    ACTIVE_DOWNLOAD,
                    String.valueOf(resourceId),
                    1L);
        } catch (RuntimeException error) {
            log.error("Redis下载计数聚合不可用，降级为数据库计数: resourceId={}", resourceId, error);
            resourceService.incrementDownloadCount(resourceId);
        }
    }

    @Override
    @Scheduled(
            fixedDelayString = "${resource.counter.flush-interval-ms:10000}",
            initialDelayString = "${resource.counter.flush-initial-delay-ms:10000}")
    public void flushPending() {
        if (!properties.isEnabled()) return;
        try {
            discoverBatches().forEach(this::flushBatch);
            rotate("view", ACTIVE_VIEW).ifPresent(this::flushBatch);
            rotate("download", ACTIVE_DOWNLOAD).ifPresent(this::flushBatch);
            batchMapper.deleteAppliedBefore(
                    LocalDateTime.now().minus(properties.getBatchRetention()));
        } catch (RuntimeException error) {
            log.error("资源计数批量写回失败，将在下次调度重试", error);
        }
    }

    private Optional<String> rotate(String type, String activeKey) {
        String batchKey = KEY_PREFIX + type + ":batch:" + UUID.randomUUID();
        Long rotated = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(activeKey, batchKey));
        return Long.valueOf(1L).equals(rotated)
                ? Optional.of(batchKey)
                : Optional.empty();
    }

    private List<String> discoverBatches() {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(BATCH_PATTERN)
                .count(properties.getScanBatchSize())
                .build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            if (cursor != null) cursor.forEachRemaining(keys::add);
        }
        return keys;
    }

    private void flushBatch(String batchKey) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(batchKey);
        if (raw.isEmpty()) {
            redisTemplate.delete(batchKey);
            return;
        }
        String type = counterType(batchKey);
        String batchId = batchKey.substring(batchKey.lastIndexOf(':') + 1);
        Map<Long, Long> deltas = new LinkedHashMap<>();
        raw.forEach((resourceId, delta) -> {
            try {
                long id = Long.parseLong(String.valueOf(resourceId));
                long value = Long.parseLong(String.valueOf(delta));
                if (id > 0 && value > 0) deltas.put(id, value);
            } catch (NumberFormatException error) {
                log.warn("忽略非法资源计数: key={}, field={}, value={}", batchKey, resourceId, delta);
            }
        });

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Boolean applied = transaction.execute(status -> applyBatch(batchId, type, deltas));
        if (Boolean.TRUE.equals(applied)) {
            redisTemplate.delete(batchKey);
            deltas.keySet().forEach(this::evictDetail);
            log.debug("资源计数批量写回完成: batchId={}, type={}, resources={}",
                    batchId, type, deltas.size());
        }
    }

    private boolean applyBatch(String batchId, String type, Map<Long, Long> deltas) {
        LocalDateTime now = LocalDateTime.now();
        if (batchMapper.insertIgnore(batchId, type, now) == 0) {
            CounterFlushBatch existing = batchMapper.selectById(batchId);
            return existing != null && "APPLIED".equals(existing.getStatus());
        }
        for (Map.Entry<Long, Long> entry : deltas.entrySet()) {
            long viewDelta = "view".equals(type) ? entry.getValue() : 0L;
            long downloadDelta = "download".equals(type) ? entry.getValue() : 0L;
            if (resourceMapper.incrementCounters(
                    entry.getKey(), viewDelta, downloadDelta) == 1) {
                outboxService.append(
                        OutboxEventType.RESOURCE_UPDATED,
                        entry.getKey(),
                        ResourceEventPayload.resource(entry.getKey()));
            }
        }
        batchMapper.markApplied(batchId, now);
        return true;
    }

    private Resource requirePublished(Long resourceId) {
        Resource resource = resourceId == null ? null : resourceMapper.selectById(resourceId);
        if (resource == null
                || Integer.valueOf(1).equals(resource.getDeleted())
                || !Integer.valueOf(1).equals(resource.getStatus())) {
            throw new BusinessException(404, "资源不存在");
        }
        return resource;
    }

    private String counterType(String batchKey) {
        if (batchKey.startsWith(KEY_PREFIX + "view:batch:")) return "view";
        if (batchKey.startsWith(KEY_PREFIX + "download:batch:")) return "download";
        throw new IllegalArgumentException("未知计数批次");
    }

    private void evictDetail(Long resourceId) {
        Cache cache = cacheManager.getCache(CacheNames.RESOURCE_DETAIL);
        if (cache != null) cache.evict(resourceId);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
