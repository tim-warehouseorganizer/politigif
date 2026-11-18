/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.seedling;

import com.kismetsoftware.politigif.user.Emails;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * What a seedling must be. The seedling is the account's only secret; it is never used to sign
 * in (the emailed link does that), only to approve account settings changes.
 *
 * <ul>
 *   <li>8 characters at least;</li>
 *   <li>72 bytes at most in UTF-8: BCrypt only reads 72 bytes, and Spring Security refuses longer
 *       input rather than silently ignoring the tail;</li>
 *   <li>typed the same twice;</li>
 *   <li>not the account's own email address.</li>
 * </ul>
 */
public final class SeedlingPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private SeedlingPolicy() {
    }

    /** The problem with this seedling, in words for the page; empty when it is acceptable. */
    public static Optional<String> problem(String seedling, String confirmation, String email) {
        if (seedling == null || seedling.isBlank()) {
            return Optional.of("Choose a seedling.");
        }
        if (seedling.codePointCount(0, seedling.length()) < MIN_LENGTH) {
            return Optional.of("Your seedling needs at least " + MIN_LENGTH + " characters.");
        }
        if (seedling.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return Optional.of("Your seedling is too long (72 bytes at most).");
        }
        if (!seedling.equals(confirmation)) {
            return Optional.of("The two seedlings don't match.");
        }
        if (Emails.normalize(seedling).equals(Emails.normalize(email))) {
            return Optional.of("Your seedling can't be your email address.");
        }
        return Optional.empty();
    }
}
