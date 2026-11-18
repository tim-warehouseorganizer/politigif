/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.ratelimit;

import java.time.Duration;

/**
 * A named bucket: how many of something, per what window (fieldbook donor shape). Per-IP is
 * always the looser of a pair: a whole office or carrier NAT can sit behind one address.
 * Capacities are overridable without a deploy: {@code app.rate-limit.capacities.signin-link:email=10}.
 */
public enum RateLimitPolicy {

    /** Sign-in links mail an address the REQUESTER chose: tight, or it is a spam cannon. */
    SIGNIN_LINK_PER_EMAIL("signin-link:email", 5, Duration.ofHours(1)),
    SIGNIN_LINK_PER_IP("signin-link:ip", 20, Duration.ofHours(1)),

    /** "Sign me in" POSTs. Tokens are unguessable; this only bounds hammering. */
    SIGNIN_REDEEM_PER_IP("signin-redeem:ip", 30, Duration.ofMinutes(15)),

    /** Seedling guesses (settings changes) for one account, and from one address across accounts. */
    SEEDLING_PER_USER("seedling:user", 5, Duration.ofMinutes(15)),
    SEEDLING_PER_IP("seedling:ip", 20, Duration.ofMinutes(15)),

    /** Each upload runs ffmpeg; this is the CPU guard. */
    UPLOAD_PER_USER("upload:user", 30, Duration.ofHours(1)),

    /** Anyone may report a GIF; bounds a flood of reports from one address. */
    REPORT_PER_IP("report:ip", 20, Duration.ofHours(1));

    private final String key;
    private final int defaultCapacity;
    private final Duration window;

    RateLimitPolicy(String key, int defaultCapacity, Duration window) {
        this.key = key;
        this.defaultCapacity = defaultCapacity;
        this.window = window;
    }

    /** The stable half of the Redis key; separate from {@code name()} so a rename doesn't reset live buckets. */
    public String key() {
        return key;
    }

    public int defaultCapacity() {
        return defaultCapacity;
    }

    public Duration window() {
        return window;
    }
}
