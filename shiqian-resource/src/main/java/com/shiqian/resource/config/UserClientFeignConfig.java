package com.shiqian.resource.config;

import com.shiqian.common.user.InternalApiHeaders;
import feign.RequestInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * 为用户服务的内部请求附加服务凭据，不记录或透传到外部响应。
 */
@EnableConfigurationProperties(ResourceUserClientProperties.class)
public class UserClientFeignConfig {

    @Bean
    public RequestInterceptor userServiceCredentialInterceptor(
            ResourceUserClientProperties properties) {
        return template -> {
            if (StringUtils.hasText(properties.getServiceKey())) {
                template.header(
                        InternalApiHeaders.SERVICE_KEY,
                        properties.getServiceKey());
            }
        };
    }
}
