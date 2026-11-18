/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.config.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code ?v=} cache-buster's resolution rules — including the blank-value trap that shipped
 * on two sibling apps: a config var set to the empty string is PRESENT, so it beat every fallback
 * and froze the version at {@code ?v=} behind a 30-day max-age.
 */
class StaticVersionAdviceTest {

    @Test
    void aRealDeployVersionIsUsedVerbatim() {
        assertEquals("v42", StaticVersionAdvice.resolve("v42"));
    }

    @Test
    void aBlankVersionIsTreatedAsAbsentNotAsAValue() {
        String resolved = StaticVersionAdvice.resolve("");

        assertTrue(resolved.startsWith("dev"),
                "a blank override must fall back — an empty ?v= token never changes across deploys");
    }

    @Test
    void theDevPlaceholderBecomesAPerRestartToken() {
        String resolved = StaticVersionAdvice.resolve("local");

        assertTrue(resolved.startsWith("dev"),
                "restarting the JVM is what busts a developer's cache");
    }

    @Test
    void nullFallsBackToo() {
        assertTrue(StaticVersionAdvice.resolve(null).startsWith("dev"));
    }
}
