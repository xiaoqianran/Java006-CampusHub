package com.shiqian.common.content;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveWordFilterTest {

    @Test
    void shouldDetectSensitiveWord() {
        SensitiveWordFilter filter = new SensitiveWordFilter(List.of("违规", "spam"));

        assertTrue(filter.contains("这是一条违规内容"));
    }

    @Test
    void shouldIgnoreCaseWhenDetectingEnglishWord() {
        SensitiveWordFilter filter = new SensitiveWordFilter(List.of("spam"));

        assertTrue(filter.contains("This is SPAM content"));
        assertEquals(Set.of("spam"), filter.findAll("This is SPAM content"));
    }

    @Test
    void shouldReturnAllMatchedWordsByInsertionOrder() {
        SensitiveWordFilter filter = new SensitiveWordFilter(List.of("违规", "违规内容", "广告"));

        Set<String> words = filter.findAll("违规内容不能包含广告");

        assertEquals(List.of("违规", "违规内容", "广告"), List.copyOf(words));
    }

    @Test
    void shouldReplaceSensitiveWord() {
        SensitiveWordFilter filter = new SensitiveWordFilter(List.of("违规", "spam"));

        assertEquals("这是一条**内容和****", filter.replace("这是一条违规内容和SPAM", '*'));
    }

    @Test
    void shouldReturnFalseForBlankTextAndEmptyDictionary() {
        SensitiveWordFilter filter = new SensitiveWordFilter(List.of());

        assertFalse(filter.contains(""));
        assertFalse(filter.contains("正常内容"));
    }

    @Test
    void shouldReloadDictionary() {
        SensitiveWordFilter filter = new SensitiveWordFilter(List.of("旧词"));

        filter.reload(List.of("新词"));

        assertFalse(filter.contains("旧词"));
        assertTrue(filter.contains("新词"));
    }

    @Test
    void shouldKeepNullTextWhenReplacing() {
        SensitiveWordFilter filter = new SensitiveWordFilter(List.of("违规"));

        assertSame(null, filter.replace(null, '*'));
    }
}
