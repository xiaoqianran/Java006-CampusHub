package com.shiqian.resource.service;

import com.shiqian.resource.dto.ResourceAuditMessage;
import com.shiqian.resource.dto.ResourceDownloadMessage;
import com.shiqian.resource.entity.UserNotification;
import com.shiqian.resource.mapper.UserNotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ResourceMessageProcessingService {

    public static final String DOWNLOAD_CONSUMER = "resource-download";
    public static final String AUDIT_CONSUMER = "resource-audit-notification";

    private final MessageIdempotencyService idempotencyService;
    private final ResourceService resourceService;
    private final UserNotificationMapper userNotificationMapper;

    @Transactional(rollbackFor = Exception.class)
    public boolean processDownload(ResourceDownloadMessage message) {
        validateDownload(message);
        if (!idempotencyService.tryStartWithinTransaction(
                message.getMessageId(), DOWNLOAD_CONSUMER)) {
            return false;
        }
        resourceService.incrementDownloadCount(message.getResourceId());
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean processAuditNotification(ResourceAuditMessage message) {
        validateAudit(message);
        if (!idempotencyService.tryStartWithinTransaction(
                message.getMessageId(), AUDIT_CONSUMER)) {
            return false;
        }

        UserNotification notification = new UserNotification();
        notification.setMessageId(message.getMessageId());
        notification.setUserId(message.getUserId());
        notification.setNotificationType("RESOURCE_AUDIT");
        notification.setTitle(auditTitle(message.getStatus()));
        notification.setContent(auditContent(message));
        notification.setRelatedId(message.getResourceId());
        notification.setReadFlag(0);
        notification.setCreateTime(
                message.getTimestamp() != null ? message.getTimestamp() : LocalDateTime.now());
        userNotificationMapper.insertIgnore(notification);
        return true;
    }

    private String auditTitle(Integer status) {
        return switch (status) {
            case 1 -> "资源审核通过";
            case 2 -> "资源需要修改";
            case 3 -> "资源审核未通过";
            case 4 -> "资源已下架";
            default -> "资源审核状态已更新";
        };
    }

    private String auditContent(ResourceAuditMessage message) {
        String base = auditTitle(message.getStatus()) + "，资源编号：" + message.getResourceId();
        return StringUtils.hasText(message.getReason())
                ? base + "，原因：" + message.getReason().trim()
                : base;
    }

    private void validateDownload(ResourceDownloadMessage message) {
        if (message == null
                || !StringUtils.hasText(message.getMessageId())
                || message.getResourceId() == null) {
            throw new IllegalArgumentException("下载消息缺少 messageId 或 resourceId");
        }
    }

    private void validateAudit(ResourceAuditMessage message) {
        if (message == null
                || !StringUtils.hasText(message.getMessageId())
                || message.getResourceId() == null
                || message.getUserId() == null
                || message.getStatus() == null) {
            throw new IllegalArgumentException("审核消息字段不完整");
        }
    }
}
