package com.shiqian.resource.storage;

import com.shiqian.resource.config.ResourceStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "resource.storage.provider", havingValue = "minio")
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient client;
    private final MinioClient presignClient;
    private final String bucket;

    public MinioObjectStorage(MinioClient client, ResourceStorageProperties properties) {
        this.client = client;
        ResourceStorageProperties.Minio minio = properties.getMinio();
        this.bucket = minio.getBucket();
        this.presignClient = StringUtils.hasText(minio.getPublicEndpoint())
                ? MinioClient.builder()
                    .endpoint(minio.getPublicEndpoint())
                    .credentials(minio.getAccessKey(), minio.getSecretKey())
                    .build()
                : null;
    }

    @PostConstruct
    public void ensurePrivateBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception error) {
            throw new IllegalStateException("MinIO bucket 初始化失败", error);
        }
    }

    @Override
    public String provider() {
        return "minio";
    }

    @Override
    public void put(String objectKey, InputStream inputStream, long size, String contentType) throws IOException {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception error) {
            throw new IOException("MinIO 上传失败", error);
        }
    }

    @Override
    public InputStream get(String objectKey) throws IOException {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception error) {
            throw new IOException("MinIO 读取失败", error);
        }
    }

    @Override
    public void delete(String objectKey) throws IOException {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception error) {
            throw new IOException("MinIO 删除失败", error);
        }
    }

    @Override
    public Optional<String> presignedGetUrl(
            String objectKey,
            Duration ttl,
            String originalName,
            boolean inline) throws IOException {
        if (presignClient == null) {
            return Optional.empty();
        }
        String disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(originalName, StandardCharsets.UTF_8)
                .build()
                .toString();
        try {
            return Optional.of(presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(Math.toIntExact(ttl.toSeconds()), TimeUnit.SECONDS)
                    .extraQueryParams(Map.of("response-content-disposition", disposition))
                    .build()));
        } catch (Exception error) {
            throw new IOException("MinIO 签名地址生成失败", error);
        }
    }
}
