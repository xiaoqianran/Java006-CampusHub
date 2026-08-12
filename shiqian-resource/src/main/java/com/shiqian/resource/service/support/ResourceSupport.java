package com.shiqian.resource.service.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.resource.dto.AttachmentCreateDTO;
import com.shiqian.resource.entity.Favorite;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.FavoriteMapper;
import com.shiqian.resource.service.ContentReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Cross-cutting helpers shared by resource command / review / query collaborators.
 */
@Component
@RequiredArgsConstructor
public class ResourceSupport {

    private final FavoriteMapper favoriteMapper;
    private final ContentReviewService contentReviewService;

    public void validateContent(
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

    public void validateContentSource(
            String contentMarkdown,
            String fileUrl,
            List<AttachmentCreateDTO> attachments,
            boolean hasExistingAttachments,
            String requestedContentType) {
        boolean hasText = StringUtils.hasText(contentMarkdown);
        boolean hasLegacyFile = StringUtils.hasText(fileUrl);
        boolean hasAttachments = hasExistingAttachments || (attachments != null && attachments.stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> StringUtils.hasText(item.getFileUrl())));
        boolean hasFiles = hasLegacyFile || hasAttachments;

        if (StringUtils.hasText(requestedContentType)) {
            String contentType = requestedContentType.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("FILE", "ARTICLE", "MIXED").contains(contentType)) {
                throw new BusinessException("内容类型不合法");
            }
        }

        if (!hasText && !hasFiles) {
            throw new BusinessException("请至少填写正文、上传图片或添加一个附件");
        }
    }

    public String normalizeContentScene(String requestedScene, String fallback) {
        if (!StringUtils.hasText(requestedScene)) {
            return StringUtils.hasText(fallback)
                    ? fallback.trim().toUpperCase(Locale.ROOT)
                    : null;
        }
        String scene = requestedScene.trim().toUpperCase(Locale.ROOT);
        if (!ResourceStatuses.CONTENT_SCENES.contains(scene)) {
            throw new BusinessException("内容频道不合法");
        }
        return scene;
    }

    public String inferContentType(
            String contentMarkdown,
            List<AttachmentCreateDTO> attachments,
            String fileUrl,
            boolean hasExistingAttachments) {
        boolean hasText = StringUtils.hasText(contentMarkdown);
        boolean hasFiles = hasExistingAttachments || StringUtils.hasText(fileUrl)
                || (attachments != null && attachments.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(item -> StringUtils.hasText(item.getFileUrl())));
        if (hasText && hasFiles) {
            return "MIXED";
        }
        return hasText ? "ARTICLE" : "FILE";
    }

    public boolean canModify(Resource resource, Long userId) {
        if (resource == null || userId == null) {
            return false;
        }
        return userId.equals(resource.getUserId())
                || SecurityUtil.hasAuthority("resource:audit");
    }

    public void clearFavorites(Long resourceId) {
        if (resourceId == null) {
            return;
        }
        favoriteMapper.delete(
                new QueryWrapper<Favorite>().eq("resource_id", resourceId));
    }

    public void applyPrimaryAttachment(
            Resource resource,
            List<AttachmentCreateDTO> attachments) {
        if (attachments == null) {
            return;
        }
        AttachmentCreateDTO primary = attachments.stream()
                .filter(Objects::nonNull)
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
