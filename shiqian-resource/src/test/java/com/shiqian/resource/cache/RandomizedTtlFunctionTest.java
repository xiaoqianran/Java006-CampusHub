package com.shiqian.resource.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomizedTtlFunctionTest {

    @Test
    void regularAndNullValuesUseSeparateBoundedTtls() {
        RandomizedTtlFunction function = new RandomizedTtlFunction(
                Duration.ofMinutes(20),
                Duration.ofSeconds(60),
                Duration.ofMinutes(5));

        for (int i = 0; i < 50; i++) {
            Duration regular = function.getTimeToLive("key", "value");
            assertTrue(regular.compareTo(Duration.ofMinutes(20)) >= 0);
            assertTrue(regular.compareTo(Duration.ofMinutes(25)) <= 0);

            Duration empty = function.getTimeToLive("key", null);
            assertTrue(empty.compareTo(Duration.ofSeconds(60)) >= 0);
            assertTrue(empty.compareTo(Duration.ofSeconds(75)) <= 0);
        }
    }

    @Test
    void nonPositiveTtlIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RandomizedTtlFunction(
                Duration.ZERO,
                Duration.ofSeconds(30),
                Duration.ZERO));
    }
}
