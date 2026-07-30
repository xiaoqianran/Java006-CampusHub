package com.shiqian.resource.tools;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JimengPromptImporterTest {

    @Test
    void normalizesWhitespaceAndTruncatesByCodePoint() {
        assertEquals(
                "校园 风景 插画",
                JimengPromptImporter.normalizeWhitespace(" 校园\n 风景\t插画 "));
        assertEquals(
                "😀😀…",
                JimengPromptImporter.truncateCodePoints("😀😀😀😀", 3));
    }

    @Test
    void rejectsNonHttpsAndUnexpectedImageHosts() {
        Set<String> allowed = Set.of("p11-dreamina-sign.byteimg.com");

        assertThrows(
                IllegalArgumentException.class,
                () -> JimengPromptImporter.validateImageUri(
                        "http://p11-dreamina-sign.byteimg.com/image.webp", allowed));
        assertThrows(
                IllegalArgumentException.class,
                () -> JimengPromptImporter.validateImageUri(
                        "https://127.0.0.1/image.webp", allowed));
        assertThrows(
                IllegalArgumentException.class,
                () -> JimengPromptImporter.validateImageUri(
                        "https://example.com/image.webp", allowed));
    }

    @Test
    void requiresCredentialsAndCapsBatchSize() {
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> JimengPromptImporter.Config.from(
                        new String[]{"--limit=10"},
                        java.util.Map.of()));
        assertTrue(missing.getMessage().contains("JIMENG_DB_HOST"));

        java.util.Map<String, String> env = java.util.Map.of(
                "JIMENG_DB_HOST", "db.example.com",
                "JIMENG_DB_NAME", "jimeng",
                "JIMENG_DB_USER", "reader",
                "JIMENG_DB_PASSWORD", "secret",
                "CAMPUSHUB_DB_PASSWORD", "local-secret");
        assertThrows(
                IllegalArgumentException.class,
                () -> JimengPromptImporter.Config.from(
                        new String[]{"--limit=5001"},
                        env));
    }
}
