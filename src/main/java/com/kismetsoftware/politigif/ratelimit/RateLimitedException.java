/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.ratelimit;

/**
 * Thrown when a bucket is empty.
 *
 * <p>Carries the retry delay because a 429 without {@code Retry-After} tells a well-behaved client
 * nothing except to guess, and clients that guess retry immediately.
 *
 * <p>Deliberately does <em>not</em> carry which policy tripped: telling an attacker whether they
 * hit the per-email or the per-IP limit tells them which one to work around.
 */
public class RateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
