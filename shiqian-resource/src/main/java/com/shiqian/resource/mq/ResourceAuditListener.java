package com.shiqian.resource.mq;

import com.shiqian.resource.config.RabbitMQConfig;
import com.shiqian.resource.dto.ResourceAuditMessage;
import com.shiqian.resource.service.ResourceMessageProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 资源审核消息监听器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceAuditListener {

    private final ResourceMessageProcessingService processingService;

    @RabbitListener(queues = RabbitMQConfig.RESOURCE_AUDIT_QUEUE)
    public void onAuditMessage(ResourceAuditMessage message) {
        try {
            boolean processed = processingService.processAuditNotification(message);
            log.info("资源审核通知消费完成: messageId={}, resourceId={}, processed={}",
                    message.getMessageId(), message.getResourceId(), processed);
        } catch (Exception exception) {
            log.error("资源审核通知消费失败，将进入有限重试: messageId={}, resourceId={}",
                    message != null ? message.getMessageId() : null,
                    message != null ? message.getResourceId() : null,
                    exception);
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("资源审核通知消费失败", exception);
        }
    }
}
