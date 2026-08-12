package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.outbox.OutboxEventType;
import com.shiqian.resource.outbox.OutboxService;
import com.shiqian.resource.outbox.ResourceEventPayload;
import com.shiqian.resource.service.AdminLogService;
import com.shiqian.resource.service.ContentReviewService;
import com.shiqian.resource.service.ResourceReviewService;
import com.shiqian.resource.service.support.ResourceStatuses;
import com.shiqian.resource.service.support.ResourceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceReviewServiceImpl implements ResourceReviewService {

    private final ResourceMapper resourceMapper;
    private final OutboxService outboxService;
    private final AdminLogService adminLogService;
    private final ContentReviewService contentReviewService;
    private final ResourceSupport resourceSupport;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditResource(Long resourceId, Integer status, Long operatorId) {
        String legacyReason = status != null && status >= ResourceStatuses.STATUS_NEEDS_CHANGES
                ? "管理员审核未通过"
                : null;
        applyReview(resourceId, status, legacyReason, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewResource(Long resourceId, Integer status, String reason, Long operatorId) {
        applyReview(resourceId, status, reason, operatorId);
    }

    private void applyReview(Long resourceId, Integer status, String reason, Long operatorId) {
        Resource existing = resourceMapper.selectByIdForUpdate(resourceId);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }
        if (status == null
                || status < ResourceStatuses.STATUS_PUBLISHED
                || status > ResourceStatuses.STATUS_OFFLINE) {
            throw new BusinessException("审核状态不合法");
        }
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : null;
        if ((status == ResourceStatuses.STATUS_NEEDS_CHANGES
                || status == ResourceStatuses.STATUS_REJECTED
                || status == ResourceStatuses.STATUS_OFFLINE)
                && !StringUtils.hasText(normalizedReason)) {
            throw new BusinessException("退回、拒绝或下架时必须填写原因");
        }
        if (existing.getStatus() != null
                && existing.getStatus().equals(status)
                && Objects.equals(existing.getReviewReason(), normalizedReason)) {
            throw new BusinessException(409, "资源已处于该审核状态，请勿重复操作");
        }

        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<Resource> update = new UpdateWrapper<>();
        update.eq("id", resourceId)
                .set("status", status)
                .set("reviewer_id", operatorId)
                .set("review_time", now)
                .set("review_reason",
                        status == ResourceStatuses.STATUS_NEEDS_CHANGES
                                || status == ResourceStatuses.STATUS_REJECTED
                                ? normalizedReason
                                : null)
                .set("offline_reason", status == ResourceStatuses.STATUS_OFFLINE ? normalizedReason : null);
        if (status == ResourceStatuses.STATUS_PUBLISHED) {
            update.set("published_time", now);
        }
        resourceMapper.update(null, update);
        // 任何非已发布终态都清理收藏（下架/拒绝/退回），与列表 SQL 过滤语义一致。
        if (status != ResourceStatuses.STATUS_PUBLISHED) {
            resourceSupport.clearFavorites(resourceId);
        }

        String action = switch (status) {
            case ResourceStatuses.STATUS_PUBLISHED -> "RESOURCE_APPROVE";
            case ResourceStatuses.STATUS_NEEDS_CHANGES -> "RESOURCE_NEEDS_CHANGES";
            case ResourceStatuses.STATUS_REJECTED -> "RESOURCE_REJECT";
            case ResourceStatuses.STATUS_OFFLINE -> "RESOURCE_TAKE_DOWN";
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
}
