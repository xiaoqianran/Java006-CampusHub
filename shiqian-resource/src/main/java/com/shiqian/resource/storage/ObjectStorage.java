package com.shiqian.resource.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

public interface ObjectStorage {

    String provider();

    void put(String objectKey, InputStream inputStream, long size, String contentType) throws IOException;

    InputStream get(String objectKey) throws IOException;

    void delete(String objectKey) throws IOException;

    Optional<String> presignedGetUrl(
            String objectKey,
            Duration ttl,
            String originalName,
            boolean inline) throws IOException;
}
