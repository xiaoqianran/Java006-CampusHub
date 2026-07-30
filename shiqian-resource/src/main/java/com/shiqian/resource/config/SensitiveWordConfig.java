package com.shiqian.resource.config;

import com.shiqian.common.content.SensitiveWordFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DFA 过滤器由数据库敏感词服务热更新。
 */
@Configuration
public class SensitiveWordConfig {

    @Bean
    public SensitiveWordFilter sensitiveWordFilter() {
        return new SensitiveWordFilter(java.util.List.of());
    }
}
