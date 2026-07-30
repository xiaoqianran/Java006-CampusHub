package com.shiqian.user.security;

import com.shiqian.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 校验不经过网关的服务间调用凭据。
 */
@Component
public class InternalServiceKeyValidator {

    private final String expectedServiceKey;

    public InternalServiceKeyValidator(
            @Value("${campushub.internal.service-key:}") String expectedServiceKey) {
        this.expectedServiceKey = expectedServiceKey;
    }

    public void validate(String providedServiceKey) {
        if (!StringUtils.hasText(expectedServiceKey)
                || !StringUtils.hasText(providedServiceKey)
                || !MessageDigest.isEqual(
                        expectedServiceKey.getBytes(StandardCharsets.UTF_8),
                        providedServiceKey.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(403, "服务间调用凭据无效");
        }
    }
}
