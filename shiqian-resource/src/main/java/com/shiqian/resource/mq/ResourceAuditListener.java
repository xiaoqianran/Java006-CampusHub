package com.shiqian.resource.mq;

import com.shiqian.resource.config.RabbitMQConfig;
import com.shiqian.resource.dto.ResourceAuditMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 资源审核消息监听器
 */
@Slf4j
@Component
public class ResourceAuditListener {

    @RabbitListener(queues = RabbitMQConfig.RESOURCE_AUDIT_QUEUE)
    public void onAuditMessage(ResourceAuditMessage message) {
        log.info("收到资源审核消息: resourceId={}, status={}, operatorId={}",
                message.getResourceId(), message.getStatus(), message.getOperatorId());
    }
}
