package com.shiqian.resource.storage;

import com.shiqian.resource.config.ResourceStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(
        name = "resource.storage.provider",
        havingValue = "local",
        matchIfMissing = true)
public class LocalObjectStorage implements ObjectStorage {

    private final Path root;

    public LocalObjectStorage(ResourceStorageProperties properties) {
        this.root = Path.of(properties.getLocal().getRoot()).toAbsolutePath().normalize();
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public void put(String objectKey, InputStream inputStream, long size, String contentType) throws IOException {
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".uploading");
        try {
            Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public InputStream get(String objectKey) throws IOException {
        Path target = resolve(objectKey);
        if (!Files.isRegularFile(target)) {
            throw new IOException("存储对象不存在");
        }
        return Files.newInputStream(target);
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Files.deleteIfExists(resolve(objectKey));
    }

    @Override
    public Optional<String> presignedGetUrl(
            String objectKey,
            Duration ttl,
            String originalName,
            boolean inline) {
        return Optional.empty();
    }

    private Path resolve(String objectKey) throws IOException {
        if (objectKey == null || objectKey.contains("..") || objectKey.contains("\\")) {
            throw new IOException("非法对象键");
        }
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("非法对象键");
        }
        return target;
    }
}
