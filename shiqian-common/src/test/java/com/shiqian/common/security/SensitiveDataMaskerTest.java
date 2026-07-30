package com.shiqian.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveDataMaskerTest {

    @Test
    void shouldMaskPasswordsAndBearerTokens() {
        String masked = SensitiveDataMasker.mask(
                "{\"password\":\"plain-secret\",\"refreshToken\":\"jwt-value\"} "
                        + "Authorization=Bearer aaa.bbb.ccc");

        assertFalse(masked.contains("plain-secret"));
        assertFalse(masked.contains("jwt-value"));
        assertFalse(masked.contains("aaa.bbb.ccc"));
        assertTrue(masked.contains("***"));
    }
}
