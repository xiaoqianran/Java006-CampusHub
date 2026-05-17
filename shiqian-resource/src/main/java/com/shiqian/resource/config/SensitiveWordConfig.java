package com.shiqian.resource.config;

import com.shiqian.common.content.SensitiveWordFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * 敏感词过滤配置。
 */
@Configuration
public class SensitiveWordConfig {

    @Bean
    public SensitiveWordFilter sensitiveWordFilter(
            @Value("${content.sensitive-words:违规,敏感词,广告}") String sensitiveWords) {
        List<String> words = Arrays.stream(sensitiveWords.split(","))
                .map(String::trim)
                .filter(word -> !word.isEmpty())
                .toList();
        return new SensitiveWordFilter(words);
    }
}
