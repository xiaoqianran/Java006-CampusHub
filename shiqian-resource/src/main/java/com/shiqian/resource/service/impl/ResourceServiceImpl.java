package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.resource.cache.CacheNames;
import org.springframework.security.access.prepost.PreAuthorize;
import com.shiqian.resource.dto.AttachmentCreateDTO;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceTaxonomySelection;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.entity.ResourceAttachment;
import com.shiqian.resource.mapper.ResourceAttachmentMapper;
import com.shiqian.resource.mapper.FavoriteMapper;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.outbox.OutboxEventType;
import com.shiqian.resource.outbox.OutboxService;
import com.shiqian.resource.outbox.ResourceEventPayload;
import com.shiqian.resource.service.AdminLogService;
import com.shiqian.resource.service.AuthorEnrichmentService;
import com.shiqian.resource.service.ContentReviewService;
import com.shiqian.resource.service.ResourceTaxonomyService;
import com.shiqian.resource.service.ResourceService;
import com.shiqian.resource.service.ResourceVersionService;
import com.shiqian.resource.service.StoredObjectService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_NEEDS_CHANGES = 2;
    private static final int STATUS_REJECTED = 3;
    private static final int STATUS_OFFLINE = 4;
    private static final java.util.Set<String> CONTENT_SCENES =
            java.util.Set.of("BLOG", "GALLERY", "SHARE");

    private final ResourceMapper resourceMapper;
    private final ResourceAttachmentMapper resourceAttachmentMapper;
    private final FavoriteMapper favoriteMapper;
    private final OutboxService outboxService;
    private final AdminLogService adminLogService;
    private final ContentReviewService contentReviewService;
    private final AuthorEnrichmentService authorEnrichmentService;
    private final ResourceTaxonomyService taxonomyService;
    private final ResourceVersionService resourceVersionService;
    private final StoredObjectService storedObjectService;

    @Override
    @CacheEvict(
            cacheNames = CacheNames.RESOURCE_DETAIL,
            key = "#result.id")
    @Transactional(rollbackFor = Exception.class)
    public Resource createResource(Long userId, ResourceCreateDTO dto) {
        ResourceTaxonomySelection taxonomy = taxonomyService.normalize(
                dto.getCategoryId(), dto.getCategoryIds(), dto.getTags(), dto.getTagNames());
        validateContent(
                userId,
                null,
                dto.getTitle(),
                dto.getSummary(),
                dto.getContentMarkdown(),
                taxonomy.legacyTags());
        validateContentSource(
                dto.getContentMarkdown(),
                dto.getFileUrl(),
                dto.getAttachments(),
                false,
                dto.getContentType());
        Resource resource = new Resource();
        BeanUtils.copyProperties(dto, resource);
        resource.setCategoryId(taxonomy.primaryCategoryId());
        resource.setTags(taxonomy.legacyTags());
        resource.setUserId(userId);
        resource.setDownloadCount(0);
        resource.setViewCount(0);
        resource.setVersion(1);
        resource.setStatus(STATUS_PENDING);
        resource.setContentScene(normalizeContentScene(dto.getContentScene(), "SHARE"));

        // 内容类型描述实际载荷；频道只决定展示方式，不限制正文或附件组合。
        resource.setContentType(inferContentType(
                resource.getContentMarkdown(),
                dto.getAttachments(),
                resource.getFileUrl(),
                false));
        applyPrimaryAttachment(resource, dto.getAttachments());

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
    @Cacheable(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id", sync = true)
    public Resource getResourceById(Long id) {
        Resource resource = resourceMapper.selectById(id);
        if (resource == null || resource.getDeleted() == 1) {
            return null;
        }
        List<ResourceAttachment> attachments = resourceAttachmentMapper.selectList(
            new QueryWrapper<ResourceAttachment>().eq("resource_id", id).orderByAsc("sort_order")
        );
        resource.setAttachments(attachments);
        taxonomyService.enrich(java.util.List.of(resource));
        authorEnrichmentService.enrich(java.util.List.of(resource));
        return resource;
    }

    @Override
    public List<Resource> getPublishedResourcesByIds(List<Long> ids) {
        return getPublishedResourcesByIds(ids, null);
    }

    @Override
    public List<Resource> getPublishedResourcesByIds(List<Long> ids, String contentScene) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        String scene = normalizeContentScene(contentScene, null);
        Map<Long, Resource> resourcesById = new LinkedHashMap<>();
        resourceMapper.selectBatchIds(ids).stream()
                .filter(resource -> resource.getDeleted() == null || resource.getDeleted() == 0)
                .filter(resource -> resource.getStatus() != null && resource.getStatus() == STATUS_PUBLISHED)
                .filter(resource -> scene == null || scene.equals(resource.getContentScene()))
                .forEach(resource -> resourcesById.put(resource.getId(), resource));

        List<Resource> ordered = ids.stream()
                .map(resourcesById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        authorEnrichmentService.enrich(ordered);
        enrichAttachments(ordered);
        taxonomyService.enrich(ordered);
        return ordered;
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id")
    @Transactional(rollbackFor = Exception.class)
    public void updateResource(Long userId, Long id, ResourceUpdateDTO dto) {
        Resource existing = resourceMapper.selectByIdForUpdate(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }
        if (!canModify(existing, userId)) {
            throw new BusinessException("无权更新该资源");
        }
        ResourceTaxonomySelection taxonomy = taxonomyService.normalize(
                dto.getCategoryId(), dto.getCategoryIds(), dto.getTags(), dto.getTagNames());
        validateContent(
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
        validateContentSource(
                effectiveContentMarkdown,
                effectiveFileUrl,
                dto.getAttachments(),
                keepExistingAttachments,
                dto.getContentType());

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
        resource.setStatus(existing.getStatus());
        resource.setContentScene(normalizeContentScene(
                dto.getContentScene(),
                StringUtils.hasText(existing.getContentScene()) ? existing.getContentScene() : "SHARE"));

        // 根据保存后的实际内容重新计算，而不是让频道反向限制载荷。
        resource.setContentType(inferContentType(
                effectiveContentMarkdown,
                dto.getAttachments(),
                effectiveFileUrl,
                keepExistingAttachments));
        applyPrimaryAttachment(resource, dto.getAttachments());
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

        // 如果提供了 attachments（含空列表表示清空），则替换 t_resource_attachment 中的记录
        if (dto.getAttachments() != null) {
            syncAttachments(existing.getUserId(), id, dto.getAttachments());
        }
        taxonomyService.sync(id, taxonomy);
        Resource updated = resourceMapper.selectById(id);
        resourceVersionService.recordSnapshot(updated, userId, dto.getChangeDescription());

        outboxService.append(
                OutboxEventType.RESOURCE_UPDATED,
                id,
                ResourceEventPayload.resource(id));
        log.info("资源更新成功: id={}, title={}, version={}, attachmentsProvided={}", 
                 id, resource.getTitle(), resource.getVersion(), dto.getAttachments() != null);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id")
    @Transactional(rollbackFor = Exception.class)
    public void deleteResource(Long userId, Long id) {
        Resource existing = resourceMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }
        if (!existing.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除该资源");
        }
        resourceMapper.deleteById(id);
        outboxService.append(
                OutboxEventType.RESOURCE_DELETED,
                id,
                ResourceEventPayload.resource(id));
        log.info("资源删除成功: id={}, userId={}", id, userId);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id")
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
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id")
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
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#resourceId")
    @Transactional(rollbackFor = Exception.class)
    public void auditResource(Long resourceId, Integer status, Long operatorId) {
        String legacyReason = status != null && status >= STATUS_NEEDS_CHANGES
                ? "管理员审核未通过"
                : null;
        applyReview(resourceId, status, legacyReason, operatorId);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#resourceId")
    @Transactional(rollbackFor = Exception.class)
    public void reviewResource(Long resourceId, Integer status, String reason, Long operatorId) {
        applyReview(resourceId, status, reason, operatorId);
    }

    private void applyReview(Long resourceId, Integer status, String reason, Long operatorId) {
        Resource existing = resourceMapper.selectByIdForUpdate(resourceId);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }
        if (status == null || status < STATUS_PUBLISHED || status > STATUS_OFFLINE) {
            throw new BusinessException("审核状态不合法");
        }
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : null;
        if ((status == STATUS_NEEDS_CHANGES || status == STATUS_REJECTED || status == STATUS_OFFLINE)
                && !StringUtils.hasText(normalizedReason)) {
            throw new BusinessException("退回、拒绝或下架时必须填写原因");
        }
        if (existing.getStatus() != null
                && existing.getStatus().equals(status)
                && java.util.Objects.equals(existing.getReviewReason(), normalizedReason)) {
            throw new BusinessException(409, "资源已处于该审核状态，请勿重复操作");
        }

        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<Resource> update = new UpdateWrapper<>();
        update.eq("id", resourceId)
                .set("status", status)
                .set("reviewer_id", operatorId)
                .set("review_time", now)
                .set("review_reason",
                        status == STATUS_NEEDS_CHANGES || status == STATUS_REJECTED
                                ? normalizedReason
                                : null)
                .set("offline_reason", status == STATUS_OFFLINE ? normalizedReason : null);
        if (status == STATUS_PUBLISHED) {
            update.set("published_time", now);
        }
        resourceMapper.update(null, update);

        String action = switch (status) {
            case STATUS_PUBLISHED -> "RESOURCE_APPROVE";
            case STATUS_NEEDS_CHANGES -> "RESOURCE_NEEDS_CHANGES";
            case STATUS_REJECTED -> "RESOURCE_REJECT";
            case STATUS_OFFLINE -> "RESOURCE_TAKE_DOWN";
            default -> "RESOURCE_REVIEW";
        };
        adminLogService.recordLog(operatorId, action, resourceId, normalizedReason);
        contentReviewService.recordManual(
                resourceId,
                existing.getUserId(),
                operatorId,
                status,
                normalizedReason);

        outboxService.append(
                OutboxEventType.RESOURCE_AUDITED,
                resourceId,
                ResourceEventPayload.audited(
                        resourceId,
                        existing.getUserId(),
                        status,
                        operatorId,
                        normalizedReason,
                        now));
        log.info("资源审核完成: resourceId={}, status={}, operatorId={}, reason={}",
                resourceId, status, operatorId, normalizedReason);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#resourceId")
    @Transactional(rollbackFor = Exception.class)
    public void resubmitResource(Long userId, Long resourceId) {
        Resource existing = resourceMapper.selectById(resourceId);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }
        if (!canModify(existing, userId)) {
            throw new BusinessException(403, "无权重新提交该资源");
        }
        if (existing.getStatus() == null || existing.getStatus() != STATUS_NEEDS_CHANGES) {
            throw new BusinessException("只有待修改资源可以重新提交");
        }

        UpdateWrapper<Resource> update = new UpdateWrapper<>();
        update.eq("id", resourceId)
                .set("status", STATUS_PENDING)
                .set("review_reason", null)
                .set("reviewer_id", null)
                .set("review_time", null);
        resourceMapper.update(null, update);

        outboxService.append(
                OutboxEventType.RESOURCE_UPDATED,
                resourceId,
                ResourceEventPayload.resource(resourceId));
        log.info("资源重新提交成功: id={}, userId={}", resourceId, userId);
    }

    @Override
    public Page<Resource> pageResources(Integer page, Integer size, Long categoryId, String keyword, String sort) {
        return pageResources(page, size, categoryId, keyword, sort, null);
    }

    @Override
    public Page<Resource> pageResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene) {
        return pageResources(page, size, categoryId, keyword, sort, contentScene, null, null);
    }

    @Override
    public Page<Resource> pageResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene,
            Long tagId,
            String tagName) {
        Page<Resource> pageParam = new Page<>(page, size);
        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);

        applyTaxonomyFilters(wrapper, categoryId, tagId, tagName);
        applyContentSceneFilter(wrapper, contentScene);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like("title", keyword)
                    .or().like("summary", keyword)
                    .or().like("description", keyword)  // legacy 兼容旧数据
                    .or().like("tags", keyword)
                    .or().like("content_markdown", keyword));
        }

        applyListOrdering(wrapper, sort, contentScene);
        Page<Resource> result = resourceMapper.selectPage(pageParam, wrapper);
        authorEnrichmentService.enrich(result.getRecords());
        enrichAttachments(result.getRecords());
        taxonomyService.enrich(result.getRecords());
        return result;
    }

    @Override
    public Page<Resource> pagePublishedResources(Integer page, Integer size, Long categoryId, String keyword, String sort) {
        return pagePublishedResources(page, size, categoryId, keyword, sort, null);
    }

    @Override
    public Page<Resource> pagePublishedResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene) {
        return pagePublishedResources(page, size, categoryId, keyword, sort, contentScene, null, null);
    }

    @Override
    public Page<Resource> pagePublishedResources(
            Integer page,
            Integer size,
            Long categoryId,
            String keyword,
            String sort,
            String contentScene,
            Long tagId,
            String tagName) {
        Page<Resource> pageParam = new Page<>(page, size);
        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        wrapper.eq("status", 1);

        applyTaxonomyFilters(wrapper, categoryId, tagId, tagName);
        applyContentSceneFilter(wrapper, contentScene);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like("title", keyword)
                    .or().like("summary", keyword)
                    .or().like("description", keyword)
                    .or().like("tags", keyword)
                    .or().like("content_markdown", keyword));
        }

        applyListOrdering(wrapper, sort, contentScene);
        Page<Resource> result = resourceMapper.selectPage(pageParam, wrapper);
        authorEnrichmentService.enrich(result.getRecords());
        enrichAttachments(result.getRecords());
        taxonomyService.enrich(result.getRecords());
        return result;
    }

    @Override
    public Page<Resource> pageRecycleResources(Integer page, Integer size, String keyword) {
        Page<Resource> pageParam = new Page<>(page, size);
        Page<Resource> result = resourceMapper.selectRecyclePage(pageParam, keyword);
        authorEnrichmentService.enrich(result.getRecords());
        return result;
    }

    @Override
    public Page<Resource> pageUserResources(Long userId, Integer page, Integer size, String sort) {
        Page<Resource> pageParam = new Page<>(page, size);
        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        wrapper.eq("user_id", userId);

        if ("hottest".equals(sort)) {
            wrapper.orderByDesc("download_count");
            wrapper.orderByDesc("view_count");
        } else {
            wrapper.orderByDesc("create_time");
        }
        Page<Resource> result = resourceMapper.selectPage(pageParam, wrapper);
        authorEnrichmentService.enrich(result.getRecords());
        enrichAttachments(result.getRecords());
        taxonomyService.enrich(result.getRecords());
        return result;
    }

    private void validateContent(
            Long submitterId,
            Long resourceId,
            String title,
            String summary,
            String contentMarkdown,
            String tags) {
        contentReviewService.inspectOrReject(
                submitterId,
                resourceId,
                title,
                summary,
                contentMarkdown,
                tags);
    }

    private void validateContentSource(
            String contentMarkdown,
            String fileUrl,
            List<AttachmentCreateDTO> attachments,
            boolean hasExistingAttachments,
            String requestedContentType) {
        boolean hasText = StringUtils.hasText(contentMarkdown);
        boolean hasLegacyFile = StringUtils.hasText(fileUrl);
        boolean hasAttachments = hasExistingAttachments || (attachments != null && attachments.stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(item -> StringUtils.hasText(item.getFileUrl())));
        boolean hasFiles = hasLegacyFile || hasAttachments;

        if (StringUtils.hasText(requestedContentType)) {
            String contentType = requestedContentType.trim().toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("FILE", "ARTICLE", "MIXED").contains(contentType)) {
                throw new BusinessException("内容类型不合法");
            }
        }

        if (!hasText && !hasFiles) {
            throw new BusinessException("请至少填写正文、上传图片或添加一个附件");
        }
    }

    private String normalizeContentScene(String requestedScene, String fallback) {
        if (!StringUtils.hasText(requestedScene)) {
            return StringUtils.hasText(fallback)
                    ? fallback.trim().toUpperCase(java.util.Locale.ROOT)
                    : null;
        }
        String scene = requestedScene.trim().toUpperCase(java.util.Locale.ROOT);
        if (!CONTENT_SCENES.contains(scene)) {
            throw new BusinessException("内容频道不合法");
        }
        return scene;
    }

    private void applyContentSceneFilter(QueryWrapper<Resource> wrapper, String contentScene) {
        String scene = normalizeContentScene(contentScene, null);
        if (scene != null) {
            wrapper.eq("content_scene", scene);
        }
    }

    private void applyTaxonomyFilters(
            QueryWrapper<Resource> wrapper,
            Long categoryId,
            Long tagId,
            String tagName) {
        if (categoryId != null) {
            wrapper.and(group -> group
                    .eq("category_id", categoryId)
                    .or()
                    .exists("""
                            SELECT 1
                            FROM t_resource_category rc
                            WHERE rc.resource_id = t_resource.id
                              AND rc.category_id = {0}
                            """, categoryId));
        }
        if (tagId != null) {
            wrapper.exists("""
                    SELECT 1
                    FROM t_resource_tag rt
                    WHERE rt.resource_id = t_resource.id
                      AND rt.tag_id = {0}
                    """, tagId);
        }
        if (StringUtils.hasText(tagName)) {
            wrapper.and(group -> group
                    .like("tags", tagName.trim())
                    .or()
                    .exists("""
                            SELECT 1
                            FROM t_resource_tag rt
                            JOIN t_tag t ON t.id = rt.tag_id
                            WHERE rt.resource_id = t_resource.id
                              AND t.deleted = 0
                              AND t.status = 1
                              AND t.name = {0}
                            """, tagName.trim()));
        }
    }

    /**
     * 列表排序：图片频道优先有封面，避免“只有提示词”的条目霸占首页。
     */
    private void applyListOrdering(QueryWrapper<Resource> wrapper, String sort, String contentScene) {
        String scene = normalizeContentScene(contentScene, null);
        if ("GALLERY".equals(scene)) {
            if ("hottest".equals(sort)) {
                wrapper.last("ORDER BY (file_url IS NOT NULL AND TRIM(file_url) <> '') DESC, "
                        + "download_count DESC, view_count DESC, id DESC");
            } else {
                wrapper.last("ORDER BY (file_url IS NOT NULL AND TRIM(file_url) <> '') DESC, "
                        + "create_time DESC, id DESC");
            }
            return;
        }
        if ("hottest".equals(sort)) {
            wrapper.orderByDesc("download_count");
            wrapper.orderByDesc("view_count");
        } else {
            wrapper.orderByDesc("create_time");
        }
    }

    private String inferContentType(
            String contentMarkdown,
            List<AttachmentCreateDTO> attachments,
            String fileUrl,
            boolean hasExistingAttachments) {
        boolean hasText = StringUtils.hasText(contentMarkdown);
        boolean hasFiles = hasExistingAttachments || StringUtils.hasText(fileUrl)
                || (attachments != null && attachments.stream()
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(item -> StringUtils.hasText(item.getFileUrl())));
        if (hasText && hasFiles) {
            return "MIXED";
        }
        return hasText ? "ARTICLE" : "FILE";
    }

    private boolean canModify(Resource resource, Long userId) {
        if (resource == null || userId == null) {
            return false;
        }
        return userId.equals(resource.getUserId())
                || SecurityUtil.hasAuthority("resource:audit");
    }

    private void enrichAttachments(List<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            return;
        }
        List<Long> resourceIds = resources.stream()
                .map(Resource::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (resourceIds.isEmpty()) {
            return;
        }
        Map<Long, List<ResourceAttachment>> grouped = resourceAttachmentMapper.selectList(
                        new QueryWrapper<ResourceAttachment>()
                                .in("resource_id", resourceIds)
                                .orderByAsc("sort_order"))
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ResourceAttachment::getResourceId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        resources.forEach(resource ->
                resource.setAttachments(grouped.getOrDefault(resource.getId(), Collections.emptyList())));
    }

    @Override
    @PreAuthorize("hasAuthority('resource:audit')")
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id")
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
    @PreAuthorize("hasAuthority('resource:audit')")
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, key = "#id")
    @Transactional(rollbackFor = Exception.class)
    public void permanentDeleteResource(Long id) {
        storedObjectService.deleteResourceFiles(id);
        resourceAttachmentMapper.delete(
                new QueryWrapper<ResourceAttachment>().eq("resource_id", id));
        taxonomyService.removeResourceRelations(id);
        resourceVersionService.deleteVersions(id);
        favoriteMapper.delete(
                new QueryWrapper<com.shiqian.resource.entity.Favorite>().eq("resource_id", id));
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
            if (attDto == null) continue;
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

    private void applyPrimaryAttachment(
            Resource resource,
            List<AttachmentCreateDTO> attachments) {
        if (attachments == null) {
            return;
        }
        AttachmentCreateDTO primary = attachments.stream()
                .filter(java.util.Objects::nonNull)
                .filter(item -> StringUtils.hasText(item.getFileUrl()))
                .findFirst()
                .orElse(null);
        if (primary == null) {
            resource.setFileUrl("");
            resource.setFileSize(0L);
            resource.setFileType(StringUtils.hasText(resource.getContentMarkdown())
                    ? "Markdown资源"
                    : "文字资源");
            return;
        }
        resource.setFileUrl(primary.getFileUrl());
        resource.setFileSize(primary.getFileSize() == null ? 0L : primary.getFileSize());
        resource.setFileType(StringUtils.hasText(primary.getMimeType())
                ? primary.getMimeType()
                : primary.getFileType());
    }
}
