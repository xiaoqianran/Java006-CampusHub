package com.shiqian.resource.storage;

public record ValidatedFile(
        String originalName,
        String extension,
        String mimeType,
        String assetKind,
        long size) {
}
