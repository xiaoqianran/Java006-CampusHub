package com.shiqian.resource.event;

import com.shiqian.resource.dto.ResourceAuditMessage;

public record ResourceAuditCommittedEvent(ResourceAuditMessage message) {
}
