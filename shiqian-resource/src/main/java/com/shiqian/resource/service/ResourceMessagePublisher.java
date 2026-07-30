package com.shiqian.resource.service;

import com.shiqian.resource.config.RabbitMQConfig;
import com.shiqian.resource.dto.ResourceDownloadMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public String publishDownload(Long resourceId, Long userId) {
        String messageId = UUID.randomUUID().toString();
        ResourceDownloadMessage message = new ResourceDownloadMessage(
                messageId, resourceId, userId, LocalDateTime.now());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.RESOURCE_TOPIC_EXCHANGE,
                RabbitMQConfig.RESOURCE_DOWNLOAD_ROUTING_KEY,
                message,
                raw -> {
                    raw.getMessageProperties().setMessageId(messageId);
                    raw.getMessageProperties().setType("RESOURCE_DOWNLOADED");
                    raw.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return raw;
                });
        log.debug("下载计数消息已发送: messageId={}, resourceId={}", messageId, resourceId);
        return messageId;
    }
}
