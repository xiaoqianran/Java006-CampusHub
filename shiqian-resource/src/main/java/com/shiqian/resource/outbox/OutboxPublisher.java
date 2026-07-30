package com.shiqian.resource.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiqian.resource.config.RabbitMQConfig;
import com.shiqian.resource.config.ResourceMessagingProperties;
import com.shiqian.resource.dto.ResourceAuditMessage;
import com.shiqian.resource.dto.ResourceIndexMessage;
import com.shiqian.resource.entity.OutboxEvent;
import com.shiqian.resource.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "resource.messaging.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxPublisher {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ResourceMessagingProperties properties;

    @Scheduled(
            fixedDelayString = "${resource.messaging.outbox.poll-interval-ms:1000}",
            initialDelayString = "${resource.messaging.outbox.initial-delay-ms:3000}")
    public void publishReadyEvents() {
        LocalDateTime now = LocalDateTime.now();
        outboxEventMapper.recoverStaleClaims(
                now.minus(properties.getOutbox().getClaimTimeout()),
                now,
                properties.getOutbox().getMaxAttempts());
        List<OutboxEvent> ready = outboxEventMapper.selectReady(
                now, properties.getOutbox().getBatchSize());
        for (OutboxEvent event : ready) {
            if (outboxEventMapper.claim(event.getId(), LocalDateTime.now()) != 1) {
                continue;
            }
            publishClaimed(event);
        }
    }

    void publishClaimed(OutboxEvent event) {
        try {
            ResourceEventPayload payload =
                    objectMapper.readValue(event.getPayload(), ResourceEventPayload.class);
            ResourceIndexMessage indexMessage = new ResourceIndexMessage(
                    event.getMessageId(),
                    event.getId(),
                    event.getEventType(),
                    event.getAggregateId(),
                    payload.getOccurredAt());

            rabbitTemplate.invoke(operations -> {
                CorrelationData indexCorrelation = sendPersistent(
                        operations,
                        RabbitMQConfig.RESOURCE_INDEX_ROUTING_KEY,
                        event.getMessageId(),
                        event.getMessageId() + ":index",
                        event.getEventType(),
                        indexMessage);
                CorrelationData auditCorrelation = null;
                if (OutboxEventType.RESOURCE_AUDITED.name().equals(event.getEventType())) {
                    ResourceAuditMessage auditMessage = new ResourceAuditMessage(
                            event.getMessageId(),
                            event.getId(),
                            event.getAggregateId(),
                            payload.getUserId(),
                            payload.getStatus(),
                            payload.getOperatorId(),
                            payload.getReason(),
                            payload.getOccurredAt());
                    auditCorrelation = sendPersistent(
                            operations,
                            RabbitMQConfig.RESOURCE_AUDIT_ROUTING_KEY,
                            event.getMessageId(),
                            event.getMessageId() + ":audit",
                            event.getEventType(),
                            auditMessage);
                }
                operations.waitForConfirmsOrDie(
                        properties.getOutbox().getConfirmTimeout().toMillis());
                assertRouted(indexCorrelation);
                if (auditCorrelation != null) {
                    assertRouted(auditCorrelation);
                }
                return null;
            });

            outboxEventMapper.markPublished(event.getId(), LocalDateTime.now());
            log.debug("Outbox 事件发布成功: eventId={}, messageId={}, type={}",
                    event.getId(), event.getMessageId(), event.getEventType());
        } catch (Exception exception) {
            markFailure(event, exception);
        }
    }

    private CorrelationData sendPersistent(
            RabbitOperations operations,
            String routingKey,
            String messageId,
            String correlationId,
            String eventType,
            Object payload) {
        CorrelationData correlationData = new CorrelationData(correlationId);
        operations.convertAndSend(
                RabbitMQConfig.RESOURCE_TOPIC_EXCHANGE,
                routingKey,
                payload,
                message -> {
                    message.getMessageProperties().setMessageId(messageId);
                    message.getMessageProperties().setType(eventType);
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                },
                correlationData);
        return correlationData;
    }

    private void assertRouted(CorrelationData correlationData) {
        if (correlationData.getReturned() != null) {
            throw new AmqpException(
                    "message was returned: " + correlationData.getReturned().getReplyText());
        }
    }

    private void markFailure(OutboxEvent event, Exception exception) {
        int retryCount = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
        boolean exhausted = retryCount >= properties.getOutbox().getMaxAttempts();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRetry = exhausted
                ? now
                : now.plus(backoff(retryCount));
        String error = exception.getMessage() != null
                ? exception.getClass().getSimpleName() + ": " + exception.getMessage()
                : exception.getClass().getSimpleName();
        if (error.length() > MAX_ERROR_LENGTH) {
            error = error.substring(0, MAX_ERROR_LENGTH);
        }
        outboxEventMapper.markFailed(
                event.getId(),
                exhausted ? OutboxStatus.DEAD.name() : OutboxStatus.FAILED.name(),
                retryCount,
                nextRetry,
                error,
                now);
        log.error(
                "Outbox 事件发布失败: eventId={}, messageId={}, retryCount={}, exhausted={}",
                event.getId(), event.getMessageId(), retryCount, exhausted, exception);
    }

    private Duration backoff(int retryCount) {
        Duration base = properties.getOutbox().getBaseBackoff();
        Duration max = properties.getOutbox().getMaxBackoff();
        long multiplier = 1L << Math.min(Math.max(retryCount - 1, 0), 20);
        long millis;
        try {
            millis = Math.multiplyExact(base.toMillis(), multiplier);
        } catch (ArithmeticException ignored) {
            millis = max.toMillis();
        }
        return Duration.ofMillis(Math.min(millis, max.toMillis()));
    }
}
