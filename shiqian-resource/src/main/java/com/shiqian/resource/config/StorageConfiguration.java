package com.shiqian.resource.config;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(ResourceStorageProperties.class)
public class StorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "resource.storage.provider", havingValue = "minio")
    public MinioClient minioClient(ResourceStorageProperties properties) {
        ResourceStorageProperties.Minio minio = properties.getMinio();
        if (!StringUtils.hasText(minio.getAccessKey()) || !StringUtils.hasText(minio.getSecretKey())) {
            throw new IllegalStateException("启用 MinIO 时必须配置 MINIO_ACCESS_KEY 和 MINIO_SECRET_KEY");
        }
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }
}
