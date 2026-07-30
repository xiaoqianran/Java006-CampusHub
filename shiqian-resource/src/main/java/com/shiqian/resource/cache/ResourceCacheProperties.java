package com.shiqian.resource.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "resource.cache")
public class ResourceCacheProperties {

    private Duration defaultTtl = Duration.ofMinutes(15);
    private Duration resourceDetailTtl = Duration.ofMinutes(20);
    private Duration categoryTreeTtl = Duration.ofMinutes(60);
    private Duration nullTtl = Duration.ofSeconds(60);
    private Duration jitter = Duration.ofMinutes(5);
}
