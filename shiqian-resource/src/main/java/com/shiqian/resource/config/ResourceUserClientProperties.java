package com.shiqian.resource.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户服务调用配置。服务密钥只从部署环境注入。
 */
@Data
@ConfigurationProperties(prefix = "resource.user-client")
public class ResourceUserClientProperties {

    private String serviceKey;
}
