package com.shiqian.resource.mq;

import com.shiqian.resource.config.RabbitMQConfig;
import com.shiqian.resource.dto.ResourceDownloadMessage;
import com.shiqian.resource.service.ResourceService;
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

    private final ResourceService resourceService;

    @RabbitListener(queues = RabbitMQConfig.RESOURCE_DOWNLOAD_QUEUE)
    public void onDownloadMessage(ResourceDownloadMessage message) {
        log.info("收到资源下载消息: resourceId={}, userId={}",
                message.getResourceId(), message.getUserId());
        resourceService.incrementDownloadCount(message.getResourceId());
    }
}
