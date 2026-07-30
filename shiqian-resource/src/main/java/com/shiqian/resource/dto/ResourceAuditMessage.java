package com.shiqian.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资源审核状态变更消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceAuditMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String messageId;

    private Long eventId;

    private Long resourceId;

    private Long userId;

    private Integer status;

    private Long operatorId;

    private String reason;

    private LocalDateTime timestamp;

    /**
     * 保留旧构造方式，兼容已有调用方；新消息必须由 Outbox 发布器补齐唯一 ID。
     */
    public ResourceAuditMessage(
            Long resourceId,
            Integer status,
            Long operatorId,
            LocalDateTime timestamp) {
        this(null, null, resourceId, null, status, operatorId, null, timestamp);
    }
}
