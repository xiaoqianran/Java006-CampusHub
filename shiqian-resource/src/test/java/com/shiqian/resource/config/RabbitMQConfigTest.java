package com.shiqian.resource.config;

import com.shiqian.resource.BaseResourceTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RabbitMQ 配置测试类
 */
class RabbitMQConfigTest extends BaseResourceTest {

    @Autowired
    private TopicExchange resourceTopicExchange;

    @Autowired
    private Queue resourceAuditQueue;

    @Autowired
    private Queue resourceDownloadQueue;

    @Test
    void shouldLoadTopicExchange() {
        assertNotNull(resourceTopicExchange);
        assertEquals(RabbitMQConfig.RESOURCE_TOPIC_EXCHANGE, resourceTopicExchange.getName());
        assertTrue(resourceTopicExchange.isDurable());
    }

    @Test
    void shouldLoadAuditQueue() {
        assertNotNull(resourceAuditQueue);
        assertEquals(RabbitMQConfig.RESOURCE_AUDIT_QUEUE, resourceAuditQueue.getName());
        assertTrue(resourceAuditQueue.isDurable());
    }

    @Test
    void shouldLoadDownloadQueue() {
        assertNotNull(resourceDownloadQueue);
        assertEquals(RabbitMQConfig.RESOURCE_DOWNLOAD_QUEUE, resourceDownloadQueue.getName());
        assertTrue(resourceDownloadQueue.isDurable());
    }
}
