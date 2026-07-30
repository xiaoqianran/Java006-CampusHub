package com.shiqian.resource.mq;

import com.shiqian.resource.config.RabbitMQConfig;
import com.shiqian.resource.dto.ResourceDownloadMessage;
import com.shiqian.resource.monitoring.ResourceBusinessMetrics;
import com.shiqian.resource.service.ResourceMessageProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 资源下载消息监听器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceDownloadListener {

    private final ResourceMessageProcessingService processingService;
    private final ResourceBusinessMetrics businessMetrics;

    @RabbitListener(queues = RabbitMQConfig.RESOURCE_DOWNLOAD_QUEUE)
    public void onDownloadMessage(ResourceDownloadMessage message) {
        try {
            boolean processed = processingService.processDownload(message);
            log.info("资源下载消息消费完成: messageId={}, resourceId={}, processed={}",
                    message.getMessageId(), message.getResourceId(), processed);
        } catch (Exception exception) {
            log.error("资源下载消息消费失败，将进入有限重试: messageId={}, resourceId={}",
                    message != null ? message.getMessageId() : null,
                    message != null ? message.getResourceId() : null,
                    exception);
            if (exception instanceof RuntimeException runtimeException) {
                businessMetrics.rabbitConsumeFailed();
                throw runtimeException;
            }
            businessMetrics.rabbitConsumeFailed();
            throw new IllegalStateException("资源下载消息消费失败", exception);
        }
    }
}
