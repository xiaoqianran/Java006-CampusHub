package com.shiqian.resource.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shiqian.resource.config.ResourceMessagingProperties;
import com.shiqian.resource.entity.OutboxEvent;
import com.shiqian.resource.mapper.OutboxEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventMapper outboxEventMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RabbitOperations rabbitOperations;

    private ResourceMessagingProperties properties;
    private ObjectMapper objectMapper;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new ResourceMessagingProperties();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        publisher = new OutboxPublisher(
                outboxEventMapper, objectMapper, rabbitTemplate, properties);
    }

    @Test
    void confirmedEventMustBeMarkedPublished() throws Exception {
        OutboxEvent event = event(OutboxEventType.RESOURCE_CREATED, 0);
        when(outboxEventMapper.selectReady(any(), anyInt())).thenReturn(List.of(event));
        when(outboxEventMapper.claim(eq(event.getId()), any())).thenReturn(1);
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(rabbitOperations);
        });

        publisher.publishReadyEvents();

        verify(rabbitOperations).waitForConfirmsOrDie(anyLong());
        verify(outboxEventMapper).markPublished(eq(event.getId()), any());
        verify(outboxEventMapper, never()).markFailed(
                anyLong(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void publishFailureMustScheduleBoundedRetry() throws Exception {
        OutboxEvent event = event(OutboxEventType.RESOURCE_UPDATED, 0);
        doThrow(new AmqpException("broker unavailable"))
                .when(rabbitTemplate).invoke(any());

        publisher.publishClaimed(event);

        verify(outboxEventMapper).markFailed(
                eq(event.getId()),
                eq(OutboxStatus.FAILED.name()),
                eq(1),
                any(),
                any(),
                any());
        verify(outboxEventMapper, never()).markPublished(anyLong(), any());
    }

    @Test
    void exhaustedEventMustBecomeDeadInsteadOfRetryingForever() throws Exception {
        properties.getOutbox().setMaxAttempts(2);
        OutboxEvent event = event(OutboxEventType.RESOURCE_DELETED, 1);
        doThrow(new AmqpException("broker unavailable"))
                .when(rabbitTemplate).invoke(any());

        publisher.publishClaimed(event);

        verify(outboxEventMapper).markFailed(
                eq(event.getId()),
                eq(OutboxStatus.DEAD.name()),
                eq(2),
                any(),
                any(),
                any());
    }

    @Test
    void auditedEventMustPublishIndexAndUserNotificationMessagesTogether() throws Exception {
        ResourceEventPayload payload = ResourceEventPayload.audited(
                42L, 9L, 2, 3L, "需要补充来源", LocalDateTime.now());
        OutboxEvent event = event(OutboxEventType.RESOURCE_AUDITED, 0);
        event.setPayload(objectMapper.writeValueAsString(payload));
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(rabbitOperations);
        });

        publisher.publishClaimed(event);

        verify(rabbitOperations, times(2)).convertAndSend(
                eq("resource.topic"),
                any(),
                any(),
                any(),
                any());
        verify(outboxEventMapper).markPublished(eq(event.getId()), any());
    }

    private OutboxEvent event(OutboxEventType type, int retryCount) throws Exception {
        ResourceEventPayload payload = ResourceEventPayload.resource(42L);
        OutboxEvent event = new OutboxEvent();
        event.setId(7L);
        event.setMessageId("message-7");
        event.setEventType(type.name());
        event.setAggregateType("RESOURCE");
        event.setAggregateId(42L);
        event.setPayload(objectMapper.writeValueAsString(payload));
        event.setStatus(OutboxStatus.PENDING.name());
        event.setRetryCount(retryCount);
        event.setNextRetryTime(LocalDateTime.now());
        return event;
    }
}
