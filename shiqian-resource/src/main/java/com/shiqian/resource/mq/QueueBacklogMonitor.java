package com.shiqian.resource.mq;

import com.shiqian.resource.config.RabbitMQConfig;
import com.shiqian.resource.config.ResourceMessagingProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "resource.messaging.monitor",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class QueueBacklogMonitor {

    private static final List<String> QUEUES = List.of(
            RabbitMQConfig.RESOURCE_INDEX_QUEUE,
            RabbitMQConfig.RESOURCE_AUDIT_QUEUE,
            RabbitMQConfig.RESOURCE_DOWNLOAD_QUEUE,
            RabbitMQConfig.RESOURCE_INDEX_DLQ,
            RabbitMQConfig.RESOURCE_AUDIT_DLQ,
            RabbitMQConfig.RESOURCE_DOWNLOAD_DLQ);

    private final AmqpAdmin amqpAdmin;
    private final MeterRegistry meterRegistry;
    private final ResourceMessagingProperties properties;
    private final Map<String, AtomicLong> messageCounts = new LinkedHashMap<>();

    @PostConstruct
    void registerGauges() {
        QUEUES.forEach(queue -> {
            AtomicLong value = new AtomicLong();
            messageCounts.put(queue, value);
            meterRegistry.gauge(
                    "rabbitmq.queue.messages",
                    Tags.of("queue", queue),
                    value);
        });
    }

    @Scheduled(
            fixedDelayString = "${resource.messaging.monitor.poll-interval-ms:15000}",
            initialDelayString = "${resource.messaging.monitor.initial-delay-ms:15000}")
    public void collect() {
        for (String queue : QUEUES) {
            try {
                QueueInformation info = amqpAdmin.getQueueInfo(queue);
                long count = info != null ? info.getMessageCount() : 0;
                messageCounts.get(queue).set(count);
                if (count >= properties.getMonitor().getWarningThreshold()) {
                    log.warn("RabbitMQ 队列积压: queue={}, messages={}", queue, count);
                }
            } catch (Exception exception) {
                log.error("读取 RabbitMQ 队列状态失败: queue={}", queue, exception);
            }
        }
    }
}
