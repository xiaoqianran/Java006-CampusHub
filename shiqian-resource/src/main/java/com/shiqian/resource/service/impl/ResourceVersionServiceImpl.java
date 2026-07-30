package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.resource.cache.CacheNames;
import com.shiqian.resource.dto.ResourceTaxonomySelection;
import com.shiqian.resource.dto.ResourceVersionVO;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.entity.ResourceAttachment;
import com.shiqian.resource.entity.ResourceVersion;
import com.shiqian.resource.mapper.ResourceAttachmentMapper;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.mapper.ResourceVersionMapper;
import com.shiqian.resource.outbox.OutboxEventType;
import com.shiqian.resource.outbox.OutboxService;
import com.shiqian.resource.outbox.ResourceEventPayload;
import com.shiqian.resource.service.ResourceTaxonomyService;
import com.shiqian.resource.service.ResourceVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceVersionServiceImpl implements ResourceVersionService {

    private static final int STATUS_PENDING = 0;

    private final ResourceMapper resourceMapper;
    private final ResourceVersionMapper resourceVersionMapper;
    private final ResourceAttachmentMapper attachmentMapper;
    private final ResourceTaxonomyService taxonomyService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Override
    public void ensureInitialSnapshot(Resource resource) {
        if (resourceVersionMapper.selectMaxVersion(resource.getId()) == 0) {
            recordSnapshot(resource, resource.getUserId(), "历史资源初始快照");
        }
    }

    @Override
    public void recordSnapshot(Resource resource, Long actorId, String changeDescription) {
        ResourceTaxonomySelection selection = taxonomyService.selectionOf(resource);
        List<ResourceAttachment> attachments = loadAttachments(resource.getId());

        ResourceVersion version = new ResourceVersion();
        version.setResourceId(resource.getId());
        version.setVersionNumber(resource.getVersion() == null ? 1 : resource.getVersion());
        version.setTitle(resource.getTitle());
        version.setSummary(resource.getSummary());
        version.setDescription(resource.getDescription());
        version.setMarkdownContent(resource.getContentMarkdown());
        version.setCategoryId(selection.primaryCategoryId());
        version.setTags(selection.legacyTags());
        version.setContentScene(defaultText(resource.getContentScene(), "SHARE"));
        version.setResourceType(defaultText(resource.getContentType(), "ARTICLE"));
        version.setFileUrl(resource.getFileUrl());
        version.setFileSize(resource.getFileSize() == null ? 0L : resource.getFileSize());
        version.setFileType(resource.getFileType());
        version.setCategoryIdsJson(writeJson(selection.categoryIds()));
        version.setTagNamesJson(writeJson(selection.tagNames()));
        version.setAttachmentsJson(writeJson(attachments));
        version.setChangeDescription(StringUtils.hasText(changeDescription)
                ? changeDescription.trim()
                : null);
        version.setCreatedBy(actorId == null ? resource.getUserId() : actorId);
        resourceVersionMapper.insert(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ResourceVersionVO> listVersions(Long actorId, Long resourceId) {
        Resource resource = requireModifiableResource(actorId, resourceId, false);
        ensureInitialSnapshot(resource);
        return resourceVersionMapper.selectList(new QueryWrapper<ResourceVersion>()
                        .eq("resource_id", resourceId)
                        .orderByDesc("version_number"))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceVersionVO getVersion(Long actorId, Long resourceId, Integer versionNumber) {
        Resource resource = requireModifiableResource(actorId, resourceId, false);
        ensureInitialSnapshot(resource);
        return toVO(requireVersion(resourceId, versionNumber));
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#resourceId")
    @Transactional(rollbackFor = Exception.class)
    public Resource rollback(
            Long actorId,
            Long resourceId,
            Integer versionNumber,
            String changeDescription) {
        Resource current = requireModifiableResource(actorId, resourceId, true);
        ensureInitialSnapshot(current);
        ResourceVersion target = requireVersion(resourceId, versionNumber);
        int nextVersion = Math.max(
                current.getVersion() == null ? 1 : current.getVersion(),
                resourceVersionMapper.selectMaxVersion(resourceId)) + 1;

        resourceMapper.update(null, new UpdateWrapper<Resource>()
                .eq("id", resourceId)
                .set("title", target.getTitle())
                .set("summary", target.getSummary())
                .set("description", target.getDescription())
                .set("content_markdown", target.getMarkdownContent())
                .set("content_type", target.getResourceType())
                .set("content_scene", target.getContentScene())
                .set("category_id", target.getCategoryId())
                .set("tags", target.getTags())
                .set("file_url", target.getFileUrl())
                .set("file_size", target.getFileSize())
                .set("file_type", target.getFileType())
                .set("version", nextVersion)
                .set("status", STATUS_PENDING)
                .set("review_reason", null)
                .set("reviewer_id", null)
                .set("review_time", null)
                .set("offline_reason", null)
                .set("update_time", LocalDateTime.now()));

        ResourceTaxonomySelection targetTaxonomy = new ResourceTaxonomySelection(
                readJson(target.getCategoryIdsJson(), new TypeReference<List<Long>>() {}),
                readJson(target.getTagNamesJson(), new TypeReference<List<String>>() {}));
        taxonomyService.sync(resourceId, targetTaxonomy);
        restoreAttachments(resourceId, readJson(
                target.getAttachmentsJson(),
                new TypeReference<List<ResourceAttachment>>() {}));

        Resource restored = resourceMapper.selectById(resourceId);
        restored.setVersion(nextVersion);
        String description = StringUtils.hasText(changeDescription)
                ? changeDescription.trim()
                : "回滚到版本 " + versionNumber;
        recordSnapshot(restored, actorId, description);
        outboxService.append(
                OutboxEventType.RESOURCE_UPDATED,
                resourceId,
                ResourceEventPayload.resource(resourceId));
        return restored;
    }

    @Override
    public void deleteVersions(Long resourceId) {
        resourceVersionMapper.delete(
                new QueryWrapper<ResourceVersion>().eq("resource_id", resourceId));
    }

    private Resource requireModifiableResource(Long actorId, Long resourceId, boolean lock) {
        Resource resource = lock
                ? resourceMapper.selectByIdForUpdate(resourceId)
                : resourceMapper.selectById(resourceId);
        if (resource == null || (resource.getDeleted() != null && resource.getDeleted() == 1)) {
            throw new BusinessException("资源不存在");
        }
        if (!resource.getUserId().equals(actorId)
                && !SecurityUtil.hasAuthority("resource:audit")) {
            throw new BusinessException(403, "无权查看或操作该资源版本");
        }
        return resource;
    }

    private ResourceVersion requireVersion(Long resourceId, Integer versionNumber) {
        ResourceVersion version = resourceVersionMapper.selectOne(
                new QueryWrapper<ResourceVersion>()
                        .eq("resource_id", resourceId)
                        .eq("version_number", versionNumber));
        if (version == null) {
            throw new BusinessException("指定资源版本不存在");
        }
        return version;
    }

    private List<ResourceAttachment> loadAttachments(Long resourceId) {
        return attachmentMapper.selectList(new QueryWrapper<ResourceAttachment>()
                .eq("resource_id", resourceId)
                .orderByAsc("sort_order"));
    }

    private void restoreAttachments(Long resourceId, List<ResourceAttachment> attachments) {
        attachmentMapper.delete(
                new QueryWrapper<ResourceAttachment>().eq("resource_id", resourceId));
        for (ResourceAttachment snapshot : attachments) {
            ResourceAttachment attachment = new ResourceAttachment();
            attachment.setResourceId(resourceId);
            attachment.setFileName(snapshot.getFileName());
            attachment.setFileUrl(snapshot.getFileUrl());
            attachment.setFileSize(snapshot.getFileSize());
            attachment.setFileType(snapshot.getFileType());
            attachment.setMimeType(snapshot.getMimeType());
            attachment.setAssetKind(snapshot.getAssetKind());
            attachment.setUsageType(snapshot.getUsageType());
            attachment.setSortOrder(snapshot.getSortOrder());
            attachmentMapper.insert(attachment);
        }
    }

    private ResourceVersionVO toVO(ResourceVersion version) {
        ResourceVersionVO vo = new ResourceVersionVO();
        vo.setId(version.getId());
        vo.setResourceId(version.getResourceId());
        vo.setVersionNumber(version.getVersionNumber());
        vo.setTitle(version.getTitle());
        vo.setSummary(version.getSummary());
        vo.setDescription(version.getDescription());
        vo.setMarkdownContent(version.getMarkdownContent());
        vo.setCategoryIds(readJson(
                version.getCategoryIdsJson(), new TypeReference<List<Long>>() {}));
        vo.setTagNames(readJson(
                version.getTagNamesJson(), new TypeReference<List<String>>() {}));
        vo.setContentScene(version.getContentScene());
        vo.setResourceType(version.getResourceType());
        vo.setFileUrl(version.getFileUrl());
        vo.setFileSize(version.getFileSize());
        vo.setFileType(version.getFileType());
        vo.setAttachments(readJson(
                version.getAttachmentsJson(), new TypeReference<List<ResourceAttachment>>() {}));
        vo.setChangeDescription(version.getChangeDescription());
        vo.setCreatedBy(version.getCreatedBy());
        vo.setCreateTime(version.getCreateTime());
        return vo;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("资源版本快照序列化失败");
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(StringUtils.hasText(json) ? json : "[]", type);
        } catch (JsonProcessingException e) {
            throw new BusinessException("资源版本快照损坏，无法读取");
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
