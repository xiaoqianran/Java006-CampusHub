package com.shiqian.resource.outbox;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceEventPayload {

    private Long resourceId;
    private Long userId;
    private Integer status;
    private Long operatorId;
    private String reason;
    private LocalDateTime occurredAt;

    public static ResourceEventPayload resource(Long resourceId) {
        return new ResourceEventPayload(
                resourceId, null, null, null, null, LocalDateTime.now());
    }

    public static ResourceEventPayload audited(
            Long resourceId,
            Long userId,
            Integer status,
            Long operatorId,
            String reason,
            LocalDateTime occurredAt) {
        return new ResourceEventPayload(
                resourceId, userId, status, operatorId, reason, occurredAt);
    }
}
