package com.shiqian.resource.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

/**
 * RabbitMQ 配置类
 *
 * @author shiqian
 */
@Configuration
@Slf4j
public class RabbitMQConfig {

    public static final String RESOURCE_TOPIC_EXCHANGE = "resource.topic";
    public static final String RESOURCE_DEAD_LETTER_EXCHANGE = "resource.dlx";
    public static final String RESOURCE_INDEX_QUEUE = "resource.index.queue";
    public static final String RESOURCE_AUDIT_QUEUE = "resource.audit.queue";
    public static final String RESOURCE_DOWNLOAD_QUEUE = "resource.download.queue";
    public static final String RESOURCE_INDEX_DLQ = "resource.index.dlq";
    public static final String RESOURCE_AUDIT_DLQ = "resource.audit.dlq";
    public static final String RESOURCE_DOWNLOAD_DLQ = "resource.download.dlq";
    public static final String RESOURCE_INDEX_ROUTING_KEY = "resource.index";
    public static final String RESOURCE_AUDIT_ROUTING_KEY = "resource.audit";
    public static final String RESOURCE_DOWNLOAD_ROUTING_KEY = "resource.download";
    public static final String RESOURCE_INDEX_DEAD_ROUTING_KEY = "resource.index.dead";
    public static final String RESOURCE_AUDIT_DEAD_ROUTING_KEY = "resource.audit.dead";
    public static final String RESOURCE_DOWNLOAD_DEAD_ROUTING_KEY = "resource.download.dead";

    @Bean
    public TopicExchange resourceTopicExchange() {
        return new TopicExchange(RESOURCE_TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange resourceDeadLetterExchange() {
        return new TopicExchange(RESOURCE_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue resourceIndexQueue() {
        return durableQueue(RESOURCE_INDEX_QUEUE, RESOURCE_INDEX_DEAD_ROUTING_KEY);
    }

    @Bean
    public Queue resourceAuditQueue() {
        return durableQueue(RESOURCE_AUDIT_QUEUE, RESOURCE_AUDIT_DEAD_ROUTING_KEY);
    }

    @Bean
    public Queue resourceDownloadQueue() {
        return durableQueue(RESOURCE_DOWNLOAD_QUEUE, RESOURCE_DOWNLOAD_DEAD_ROUTING_KEY);
    }

    @Bean
    public Queue resourceIndexDlq() {
        return QueueBuilder.durable(RESOURCE_INDEX_DLQ).build();
    }

    @Bean
    public Queue resourceAuditDlq() {
        return QueueBuilder.durable(RESOURCE_AUDIT_DLQ).build();
    }

    @Bean
    public Queue resourceDownloadDlq() {
        return QueueBuilder.durable(RESOURCE_DOWNLOAD_DLQ).build();
    }

    @Bean
    public Binding resourceIndexBinding() {
        return BindingBuilder.bind(resourceIndexQueue())
                .to(resourceTopicExchange())
                .with(RESOURCE_INDEX_ROUTING_KEY);
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

    @Bean
    public Binding resourceIndexDeadBinding() {
        return BindingBuilder.bind(resourceIndexDlq())
                .to(resourceDeadLetterExchange())
                .with(RESOURCE_INDEX_DEAD_ROUTING_KEY);
    }

    @Bean
    public Binding resourceAuditDeadBinding() {
        return BindingBuilder.bind(resourceAuditDlq())
                .to(resourceDeadLetterExchange())
                .with(RESOURCE_AUDIT_DEAD_ROUTING_KEY);
    }

    @Bean
    public Binding resourceDownloadDeadBinding() {
        return BindingBuilder.bind(resourceDownloadDlq())
                .to(resourceDeadLetterExchange())
                .with(RESOURCE_DOWNLOAD_DEAD_ROUTING_KEY);
    }

    /**
     * 使用 Jackson 将对象序列化为 JSON，支持 LocalDateTime
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // 序列化时带上类型信息，consumer 也能正确反序列化
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter());
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("RabbitMQ 发布确认失败: correlationId={}, cause={}",
                        correlationData != null ? correlationData.getId() : null, cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "RabbitMQ 消息不可路由: exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()));
        return template;
    }

    private Queue durableQueue(String name, String deadRoutingKey) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(RESOURCE_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadRoutingKey)
                .build();
    }
}
