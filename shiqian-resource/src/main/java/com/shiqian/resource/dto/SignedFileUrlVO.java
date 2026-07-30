package com.shiqian.resource.dto;

import java.time.LocalDateTime;

public record SignedFileUrlVO(String url, LocalDateTime expiresAt) {
}
