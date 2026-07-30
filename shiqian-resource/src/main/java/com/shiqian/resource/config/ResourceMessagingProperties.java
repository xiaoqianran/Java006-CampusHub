package com.shiqian.resource.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "resource.messaging")
public class ResourceMessagingProperties {

    private final Outbox outbox = new Outbox();
    private final Monitor monitor = new Monitor();

    @Data
    public static class Outbox {
        private boolean enabled = true;
        private int batchSize = 50;
        private int maxAttempts = 8;
        private Duration baseBackoff = Duration.ofSeconds(5);
        private Duration maxBackoff = Duration.ofMinutes(10);
        private Duration claimTimeout = Duration.ofMinutes(2);
        private Duration confirmTimeout = Duration.ofSeconds(5);
    }

    @Data
    public static class Monitor {
        private boolean enabled = true;
        private long warningThreshold = 1000;
    }
}
