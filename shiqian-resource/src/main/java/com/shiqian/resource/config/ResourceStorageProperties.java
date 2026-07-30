package com.shiqian.resource.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "resource.storage")
public class ResourceStorageProperties {

    private String provider = "local";
    private Duration signedUrlTtl = Duration.ofMinutes(10);
    private Duration temporaryTtl = Duration.ofHours(24);
    private int cleanupBatchSize = 100;
    private final Local local = new Local();
    private final Minio minio = new Minio();

    @Data
    public static class Local {
        private String root = "uploads/objects";
    }

    @Data
    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String publicEndpoint;
        private String accessKey;
        private String secretKey;
        private String bucket = "campushub-resources";
    }
}
