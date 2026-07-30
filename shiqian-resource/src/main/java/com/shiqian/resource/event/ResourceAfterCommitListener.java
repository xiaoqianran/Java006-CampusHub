package com.shiqian.resource.event;

import com.shiqian.resource.config.RabbitMQConfig;
import com.shiqian.resource.document.ResourceDocumentMapper;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.repository.ResourceDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 阶段 1 的提交后适配器：确保 ES/MQ 失败不会回滚或污染 MySQL 事务。
 * 阶段 5 会由持久化 Outbox 替代该非持久化事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceAfterCommitListener {

    private final ResourceMapper resourceMapper;
    private final ResourceDocumentRepository resourceDocumentRepository;
    private final ResourceDocumentMapper documentMapper;
    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void synchronizeIndex(ResourceIndexEvent event) {
        try {
            if (event.operation() == ResourceIndexEvent.Operation.DELETE) {
                resourceDocumentRepository.deleteById(event.resourceId());
                return;
            }
            Resource resource = resourceMapper.selectById(event.resourceId());
            if (resource == null || Integer.valueOf(1).equals(resource.getDeleted())) {
                resourceDocumentRepository.deleteById(event.resourceId());
            } else {
                resourceDocumentRepository.save(documentMapper.fromResource(resource));
            }
        } catch (Exception e) {
            log.error("MySQL 已提交，但 ES 同步失败: resourceId={}, operation={}",
                    event.resourceId(), event.operation(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAuditMessage(ResourceAuditCommittedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.RESOURCE_TOPIC_EXCHANGE,
                    RabbitMQConfig.RESOURCE_AUDIT_ROUTING_KEY,
                    event.message());
        } catch (Exception e) {
            log.error("MySQL 已提交，但审核消息发布失败: resourceId={}",
                    event.message().getResourceId(), e);
        }
    }
}
