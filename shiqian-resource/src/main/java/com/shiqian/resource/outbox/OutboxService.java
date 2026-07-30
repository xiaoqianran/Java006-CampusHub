package com.shiqian.resource.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiqian.resource.entity.OutboxEvent;
import com.shiqian.resource.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long append(
            OutboxEventType eventType,
            Long resourceId,
            ResourceEventPayload payload) {
        if (eventType == null || resourceId == null || payload == null) {
            throw new IllegalArgumentException("outbox event fields must not be null");
        }
        OutboxEvent event = new OutboxEvent();
        event.setMessageId(UUID.randomUUID().toString());
        event.setEventType(eventType.name());
        event.setAggregateType("RESOURCE");
        event.setAggregateId(resourceId);
        event.setPayload(writePayload(payload));
        event.setStatus(OutboxStatus.PENDING.name());
        event.setRetryCount(0);
        event.setNextRetryTime(LocalDateTime.now());
        outboxEventMapper.insert(event);
        return event.getId();
    }

    @Transactional
    public boolean retryDead(Long eventId) {
        if (eventId == null) {
            return false;
        }
        return outboxEventMapper.retryDead(eventId, LocalDateTime.now()) == 1;
    }

    private String writePayload(ResourceEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化 Outbox 事件", e);
        }
    }
}
