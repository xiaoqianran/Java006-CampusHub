package com.shiqian.resource.mq;

import com.shiqian.resource.config.RabbitMQConfig;
import com.shiqian.resource.document.ResourceDocumentMapper;
import com.shiqian.resource.dto.ResourceIndexMessage;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.repository.ResourceDocumentRepository;
import com.shiqian.resource.service.MessageIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceIndexListener {

    public static final String CONSUMER_NAME = "resource-elasticsearch-index";
    private static final int STATUS_PUBLISHED = 1;

    private final ResourceMapper resourceMapper;
    private final ResourceDocumentRepository resourceDocumentRepository;
    private final ResourceDocumentMapper documentMapper;
    private final MessageIdempotencyService idempotencyService;

    @RabbitListener(queues = RabbitMQConfig.RESOURCE_INDEX_QUEUE)
    public void onIndexMessage(ResourceIndexMessage message) {
        validate(message);
        if (idempotencyService.isConsumed(message.getMessageId(), CONSUMER_NAME)) {
            log.debug("跳过重复索引消息: messageId={}", message.getMessageId());
            return;
        }

        try {
            reconcileFromMysql(message.getResourceId());
            idempotencyService.markConsumed(message.getMessageId(), CONSUMER_NAME);
            log.debug("资源索引同步完成: messageId={}, resourceId={}",
                    message.getMessageId(), message.getResourceId());
        } catch (Exception exception) {
            log.error("资源索引消费失败，将进入有限重试: messageId={}, resourceId={}",
                    message.getMessageId(), message.getResourceId(), exception);
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("资源索引消费失败", exception);
        }
    }

    void reconcileFromMysql(Long resourceId) {
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null
                || Integer.valueOf(1).equals(resource.getDeleted())
                || !Integer.valueOf(STATUS_PUBLISHED).equals(resource.getStatus())) {
            resourceDocumentRepository.deleteById(resourceId);
            return;
        }
        resourceDocumentRepository.save(documentMapper.fromResource(resource));
    }

    private void validate(ResourceIndexMessage message) {
        if (message == null
                || !StringUtils.hasText(message.getMessageId())
                || message.getResourceId() == null
                || !StringUtils.hasText(message.getEventType())) {
            throw new IllegalArgumentException("索引消息字段不完整");
        }
    }
}
