package com.shiqian.resource.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 *
 * @author shiqian
 */
@Configuration
public class RabbitMQConfig {

    public static final String RESOURCE_TOPIC_EXCHANGE = "resource.topic";
    public static final String RESOURCE_AUDIT_QUEUE = "resource.audit.queue";
    public static final String RESOURCE_DOWNLOAD_QUEUE = "resource.download.queue";
    public static final String RESOURCE_AUDIT_ROUTING_KEY = "resource.audit";
    public static final String RESOURCE_DOWNLOAD_ROUTING_KEY = "resource.download";

    @Bean
    public TopicExchange resourceTopicExchange() {
        return new TopicExchange(RESOURCE_TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public Queue resourceAuditQueue() {
        return new Queue(RESOURCE_AUDIT_QUEUE, true);
    }

    @Bean
    public Queue resourceDownloadQueue() {
        return new Queue(RESOURCE_DOWNLOAD_QUEUE, true);
    }

    @Bean
    public Binding resourceAuditBinding() {
        return BindingBuilder.bind(resourceAuditQueue())
                .to(resourceTopicExchange())
                .with(RESOURCE_AUDIT_ROUTING_KEY);
    }

    @Bean
    public Binding resourceDownloadBinding() {
        return BindingBuilder.bind(resourceDownloadQueue())
                .to(resourceTopicExchange())
                .with(RESOURCE_DOWNLOAD_ROUTING_KEY);
    }
}
