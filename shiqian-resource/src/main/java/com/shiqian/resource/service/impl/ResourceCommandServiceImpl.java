package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.resource.dto.AttachmentCreateDTO;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceTaxonomySelection;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.entity.ResourceAttachment;
import com.shiqian.resource.mapper.ResourceAttachmentMapper;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.outbox.OutboxEventType;
import com.shiqian.resource.outbox.OutboxService;
import com.shiqian.resource.outbox.ResourceEventPayload;
import com.shiqian.resource.service.AdminLogService;
import com.shiqian.resource.service.ResourceCommandService;
import com.shiqian.resource.service.ResourceTaxonomyService;
import com.shiqian.resource.service.ResourceVersionService;
import com.shiqian.resource.service.StoredObjectService;
import com.shiqian.resource.service.support.ResourceStatuses;
import com.shiqian.resource.service.support.ResourceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceCommandServiceImpl implements ResourceCommandService {

    private final ResourceMapper resourceMapper;
    private final ResourceAttachmentMapper resourceAttachmentMapper;
    private final OutboxService outboxService;
    private final AdminLogService adminLogService;
    private final ResourceTaxonomyService taxonomyService;
    private final ResourceVersionService resourceVersionService;
    private final StoredObjectService storedObjectService;
    private final ResourceSupport resourceSupport;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Resource createResource(Long userId, ResourceCreateDTO dto) {
        ResourceTaxonomySelection taxonomy = taxonomyService.normalize(
                dto.getCategoryId(), dto.getCategoryIds(), dto.getTags(), dto.getTagNames());
        resourceSupport.validateContent(
                userId,
                null,
                dto.getTitle(),
                dto.getSummary(),
                dto.getContentMarkdown(),
                taxonomy.legacyTags());
        resourceSupport.validateContentSource(
                dto.getContentMarkdown(),
                dto.getFileUrl(),
                dto.getAttachments(),
                false,
                dto.getContentType());
        storedObjectService.validateUserSubmittedFileUrls(
                userId, submittedFileUrls(dto.getFileUrl(), dto.getAttachments()));
        Resource resource = new Resource();
        BeanUtils.copyProperties(dto, resource);
        resource.setCategoryId(taxonomy.primaryCategoryId());
        resource.setTags(taxonomy.legacyTags());
        resource.setUserId(userId);
        resource.setDownloadCount(0);
        resource.setViewCount(0);
        resource.setVersion(1);
        resource.setStatus(ResourceStatuses.STATUS_PENDING);
        resource.setContentScene(resourceSupport.normalizeContentScene(dto.getContentScene(), "SHARE"));

        // 内容类型描述实际载荷；频道只决定展示方式，不限制正文或附件组合。
        resource.setContentType(resourceSupport.inferContentType(
                resource.getContentMarkdown(),
                dto.getAttachments(),
                resource.getFileUrl(),
                false));
        resourceSupport.applyPrimaryAttachment(resource, dto.getAttachments());

        // 兼容旧字段（未来逐步移除）
        if (!StringUtils.hasText(resource.getFileUrl())) {
            resource.setFileUrl("");
        }
        if (resource.getFileSize() == null) {
            resource.setFileSize(0L);
        }
        if (!StringUtils.hasText(resource.getFileType())) {
            // 第一阶段：纯 Markdown 资源默认使用 "Markdown资源"
            resource.setFileType(StringUtils.hasText(resource.getContentMarkdown()) ? "Markdown资源" : "文字资源");
        }

        resourceMapper.insert(resource);

        // 第二阶段：保存附件（使用辅助方法，支持空列表清空）
        if (dto.getAttachments() != null) {
            syncAttachments(userId, resource.getId(), dto.getAttachments());
        } else if (StringUtils.hasText(resource.getFileUrl())) {
            // 仅传 legacy fileUrl 时也要绑定对象存储，避免 TEMPORARY 被清理后链接失效。
            storedObjectService.bindResourceFiles(
                    userId, resource.getId(), java.util.List.of(resource.getFileUrl()));
        }
        taxonomyService.sync(resource.getId(), taxonomy);
        // 新主键不应存在历史快照；若测试库或人工修复后留下孤儿记录，先清理再建 v1。
        resourceVersionService.deleteVersions(resource.getId());
        resourceVersionService.recordSnapshot(resource, userId, "创建资源");

        outboxService.append(
                OutboxEventType.RESOURCE_CREATED,
                resource.getId(),
                ResourceEventPayload.resource(resource.getId()));
        log.info("资源创建成功: id={}, title={}, userId={}, attachments={}",
                resource.getId(), resource.getTitle(), userId,
                dto.getAttachments() != null ? dto.getAttachments().size() : 0);
        return resource;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateResource(Long userId, Long id, ResourceUpdateDTO dto) {
        Resource existing = resourceMapper.selectByIdForUpdate(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }
        if (!resourceSupport.canModify(existing, userId)) {
            throw new BusinessException("无权更新该资源");
        }
        ResourceTaxonomySelection taxonomy = taxonomyService.normalize(
                dto.getCategoryId(), dto.getCategoryIds(), dto.getTags(), dto.getTagNames());
        resourceSupport.validateContent(
                userId,
                id,
                dto.getTitle(),
                dto.getSummary(),
                dto.getContentMarkdown(),
                taxonomy.legacyTags());
        boolean keepExistingAttachments = dto.getAttachments() == null
                && resourceAttachmentMapper.selectCount(
                    new QueryWrapper<ResourceAttachment>().eq("resource_id", id)) > 0;
        String effectiveContentMarkdown = dto.getContentMarkdown() != null
                ? dto.getContentMarkdown()
                : existing.getContentMarkdown();
        String effectiveFileUrl = StringUtils.hasText(dto.getFileUrl())
                ? dto.getFileUrl()
                : existing.getFileUrl();
        resourceSupport.validateContentSource(
                effectiveContentMarkdown,
                effectiveFileUrl,
                dto.getAttachments(),
                keepExistingAttachments,
                dto.getContentType());
        // Validate only fields supplied by this update. Existing historical URLs are tolerated until
        // the user explicitly replaces them, avoiding a forced migration during unrelated edits.
        storedObjectService.validateUserSubmittedFileUrls(
                existing.getUserId(), submittedFileUrls(dto.getFileUrl(), dto.getAttachments()));

        resourceVersionService.ensureInitialSnapshot(existing);

        Resource resource = new Resource();
        BeanUtils.copyProperties(dto, resource);
        resource.setCategoryId(taxonomy.primaryCategoryId());
        resource.setTags(taxonomy.legacyTags());
        resource.setId(id);
        resource.setUserId(existing.getUserId());
        resource.setVersion(existing.getVersion() + 1);
        resource.setDownloadCount(existing.getDownloadCount());
        resource.setViewCount(existing.getViewCount());
        // 作者修改已发布内容必须重新进审；管理员代改可保留原状态（紧急勘误）。
        boolean ownerEdit = existing.getUserId() != null && existing.getUserId().equals(userId);
        boolean reauditPublished = ownerEdit
                && existing.getStatus() != null
                && existing.getStatus() == ResourceStatuses.STATUS_PUBLISHED;
        resource.setStatus(reauditPublished ? ResourceStatuses.STATUS_PENDING : existing.getStatus());
        resource.setContentScene(resourceSupport.normalizeContentScene(
                dto.getContentScene(),
                StringUtils.hasText(existing.getContentScene()) ? existing.getContentScene() : "SHARE"));

        // 根据保存后的实际内容重新计算，而不是让频道反向限制载荷。
        resource.setContentType(resourceSupport.inferContentType(
                effectiveContentMarkdown,
                dto.getAttachments(),
                effectiveFileUrl,
                keepExistingAttachments));
        resourceSupport.applyPrimaryAttachment(resource, dto.getAttachments());
        if (dto.getAttachments() == null
                && !StringUtils.hasText(resource.getFileUrl())
                && StringUtils.hasText(existing.getFileUrl())) {
            resource.setFileUrl(existing.getFileUrl());
        }
        if (dto.getAttachments() == null
                && resource.getFileSize() == null
                && existing.getFileSize() != null) {
            resource.setFileSize(existing.getFileSize());
        }
        if (dto.getAttachments() == null
                && !StringUtils.hasText(resource.getFileType())
                && StringUtils.hasText(existing.getFileType())) {
            resource.setFileType(existing.getFileType());
        }

        resourceMapper.updateById(resource);
        if (reauditPublished) {
            // updateById 默认可能忽略 null；显式清空审核痕迹并回到待审。
            UpdateWrapper<Resource> reaudit = new UpdateWrapper<>();
            reaudit.eq("id", id)
                    .set("status", ResourceStatuses.STATUS_PENDING)
                    .set("review_reason", null)
                    .set("reviewer_id", null)
                    .set("review_time", null)
                    .set("offline_reason", null);
            resourceMapper.update(null, reaudit);
            // 离开已发布态时清理收藏，与下架/软删保持一致，避免 isFavorited 幽灵状态。
            resourceSupport.clearFavorites(id);
        }

        // 如果提供了 attachments（含空列表表示清空），则替换 t_resource_attachment 中的记录
        if (dto.getAttachments() != null) {
            syncAttachments(existing.getUserId(), id, dto.getAttachments());
        } else if (StringUtils.hasText(resource.getFileUrl())) {
            storedObjectService.bindResourceFiles(
                    existing.getUserId(), id, java.util.List.of(resource.getFileUrl()));
        }
        taxonomyService.sync(id, taxonomy);
        Resource updated = resourceMapper.selectById(id);
        resourceVersionService.recordSnapshot(updated, userId, dto.getChangeDescription());

        outboxService.append(
                OutboxEventType.RESOURCE_UPDATED,
                id,
                ResourceEventPayload.resource(id));
        log.info("资源更新成功: id={}, title={}, version={}, reaudit={}, attachmentsProvided={}",
                id, resource.getTitle(), resource.getVersion(), reauditPublished, dto.getAttachments() != null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResource(Long userId, Long id) {
        Resource existing = resourceMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }
        // 作者可删自己的；具备 resource:audit 的管理员可软删任意资源进入回收站。
        boolean owner = existing.getUserId() != null && existing.getUserId().equals(userId);
        boolean auditor = SecurityUtil.hasAuthority("resource:audit");
        if (!owner && !auditor) {
            throw new BusinessException(403, "无权删除该资源");
        }
        resourceMapper.deleteById(id);
        // 软删后清理收藏，避免「我的收藏」出现幽灵 total/空页。
        resourceSupport.clearFavorites(id);
        outboxService.append(
                OutboxEventType.RESOURCE_DELETED,
                id,
                ResourceEventPayload.resource(id));
        log.info("资源删除成功: id={}, userId={}, admin={}", id, userId, !owner && auditor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementDownloadCount(Long id) {
        Resource existing = resourceMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }
        UpdateWrapper<Resource> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id);
        wrapper.setSql("download_count = download_count + 1");
        resourceMapper.update(null, wrapper);
        outboxService.append(
                OutboxEventType.RESOURCE_UPDATED,
                id,
                ResourceEventPayload.resource(id));
        log.info("资源下载计数增加: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementViewCount(Long id) {
        Resource existing = resourceMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }
        UpdateWrapper<Resource> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id);
        wrapper.setSql("view_count = view_count + 1");
        resourceMapper.update(null, wrapper);
        log.info("资源浏览计数增加: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmitResource(Long userId, Long resourceId) {
        Resource existing = resourceMapper.selectById(resourceId);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }
        if (!resourceSupport.canModify(existing, userId)) {
            throw new BusinessException(403, "无权重新提交该资源");
        }
        // 待修改 + 已拒绝均可重新进入审核队列；已发布/待审/下架不允许误触重提。
        Integer status = existing.getStatus();
        if (status == null
                || (status != ResourceStatuses.STATUS_NEEDS_CHANGES
                && status != ResourceStatuses.STATUS_REJECTED)) {
            throw new BusinessException("只有待修改或已拒绝的资源可以重新提交");
        }

        // 敏感词库更新后，重提必须重新过自动审核。
        resourceSupport.validateContent(
                userId,
                resourceId,
                existing.getTitle(),
                existing.getSummary(),
                existing.getContentMarkdown(),
                existing.getTags());

        UpdateWrapper<Resource> update = new UpdateWrapper<>();
        update.eq("id", resourceId)
                .set("status", ResourceStatuses.STATUS_PENDING)
                .set("review_reason", null)
                .set("offline_reason", null)
                .set("reviewer_id", null)
                .set("review_time", null);
        resourceMapper.update(null, update);

        outboxService.append(
                OutboxEventType.RESOURCE_UPDATED,
                resourceId,
                ResourceEventPayload.resource(resourceId));
        log.info("资源重新提交成功: id={}, userId={}, fromStatus={}", resourceId, userId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreResource(Long id) {
        int rows = resourceMapper.restoreById(id);
        if (rows == 0) {
            throw new BusinessException("资源不存在或不在回收站中");
        }
        Long operatorId = SecurityUtil.getCurrentUserId();
        adminLogService.recordLog(operatorId, "RESOURCE_RESTORE", id, null);
        outboxService.append(
                OutboxEventType.RESOURCE_UPDATED,
                id,
                ResourceEventPayload.resource(id));
        log.info("资源从回收站恢复: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void permanentDeleteResource(Long id) {
        storedObjectService.deleteResourceFiles(id);
        resourceAttachmentMapper.delete(
                new QueryWrapper<ResourceAttachment>().eq("resource_id", id));
        taxonomyService.removeResourceRelations(id);
        resourceVersionService.deleteVersions(id);
        resourceSupport.clearFavorites(id);
        int rows = resourceMapper.physicalDeleteById(id);
        if (rows == 0) {
            throw new BusinessException("资源不存在");
        }
        Long operatorId = SecurityUtil.getCurrentUserId();
        adminLogService.recordLog(operatorId, "RESOURCE_PERMANENT_DELETE", id, null);
        outboxService.append(
                OutboxEventType.RESOURCE_DELETED,
                id,
                ResourceEventPayload.resource(id));
        log.info("资源永久删除: id={}", id);
    }

    private List<String> submittedFileUrls(
            String legacyFileUrl,
            List<AttachmentCreateDTO> attachments) {
        List<String> urls = new ArrayList<>();
        if (StringUtils.hasText(legacyFileUrl)) {
            urls.add(legacyFileUrl);
        }
        if (attachments != null) {
            attachments.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(AttachmentCreateDTO::getFileUrl)
                    .filter(StringUtils::hasText)
                    .forEach(urls::add);
        }
        return urls;
    }

    /**
     * 附件同步辅助方法：用于创建和更新场景。
     * 如果 attachments != null，则删除该资源的所有旧附件（update场景），然后插入新列表（可为空以清空）。
     */
    private void syncAttachments(
            Long ownerId,
            Long resourceId,
            List<AttachmentCreateDTO> attachments) {
        if (attachments == null) {
            return;
        }
        // 删除旧附件（create时无影响，update时替换）
        resourceAttachmentMapper.delete(
            new QueryWrapper<ResourceAttachment>().eq("resource_id", resourceId)
        );
        for (var attDto : attachments) {
            if (attDto == null) {
                continue;
            }
            ResourceAttachment att = new ResourceAttachment();
            att.setResourceId(resourceId);
            att.setFileName(attDto.getFileName());
            att.setFileUrl(attDto.getFileUrl());
            att.setFileSize(attDto.getFileSize());
            att.setFileType(attDto.getFileType());
            att.setMimeType(attDto.getMimeType());
            att.setAssetKind(attDto.getAssetKind() != null ? attDto.getAssetKind() : "FILE");
            att.setUsageType(attDto.getUsageType() != null ? attDto.getUsageType() : "ATTACHMENT");
            att.setSortOrder(attDto.getSortOrder() != null ? attDto.getSortOrder() : 0);
            resourceAttachmentMapper.insert(att);
        }
        storedObjectService.bindResourceFiles(
                ownerId,
                resourceId,
                attachments.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(AttachmentCreateDTO::getFileUrl)
                        .filter(StringUtils::hasText)
                        .toList());
    }
}
