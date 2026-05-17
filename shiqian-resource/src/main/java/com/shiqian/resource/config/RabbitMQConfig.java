package com.shiqian.resource.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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
        return template;
    }
}
