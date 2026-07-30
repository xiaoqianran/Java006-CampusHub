package com.shiqian.resource.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "resource.counter")
public class ResourceCounterProperties {

    private boolean enabled = true;
    private Duration viewDedupTtl = Duration.ofMinutes(30);
    private int scanBatchSize = 100;
    private Duration batchRetention = Duration.ofDays(7);
}
