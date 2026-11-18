/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Distributed rate limiting over the Redis we already run for sessions — ported from citysurfing
 * (flamelens lineage).
 *
 * <p>Called explicitly from the endpoints that need it rather than installed as a blanket filter,
 * because the dimension that matters most — the email in a token request, the user behind a pin —
 * lives in the request body or the principal, which a filter can't see.
 *
 * <p><strong>Fails open.</strong> If Redis is unreachable the request is allowed, with a warning.
 * The alternative is that a Redis outage takes down sign-in for everyone. A Redis outage also
 * takes sessions down, so in practice nobody is signing in then anyway; bcrypt still bounds
 * guessing speed per attempt.
 */
@Service
public class RateLimiter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RateLimiter.class);

    private final ProxyManager<String> buckets;
    private final RateLimitProperties properties;
    private final String keyPrefix;

    public RateLimiter(ProxyManager<String> rateLimitBuckets,
                       RateLimitProperties properties,
                       @Value("${app.redis.key-prefix:politigif}") String keyPrefix) {
        this.buckets = rateLimitBuckets;
        this.properties = properties;
        // bucket4j writes raw keys — Redisson's NameMapper never sees them — so the tenant prefix
        // has to be baked into the key itself or this app shares buckets with its siblings.
        this.keyPrefix = keyPrefix;
    }

    /**
     * Consumes one token, or throws.
     *
     * @param value the dimension's value — an IP, an email, a user uuid. Hashed before it reaches
     *              Redis, so a dump of the keyspace is not a list of everyone's addresses.
     */
    public void require(RateLimitPolicy policy, String value) {
        if (!properties.enabled() || value == null || value.isBlank()) {
            return;
        }
        String key = keyPrefix + ":rl:" + policy.key() + ":" + hash(value);
        int capacity = properties.capacityFor(policy);

        try {
            ConsumptionProbe probe = buckets.builder()
                    .build(key, () -> configurationFor(policy, capacity))
                    .tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                long retryAfter = (probe.getNanosToWaitForRefill() / 1_000_000_000L) + 1;
                log.info("rate limit hit: policy={} retryAfter={}s", policy.key(), retryAfter);
                throw new RateLimitedException(
                        "You're going a bit fast. Try again in " + humanDelay(retryAfter) + ".",
                        retryAfter);
            }
        } catch (RateLimitedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("rate limiter unavailable — allowing the request (policy {})", policy.key(), e);
        }
    }

    /**
     * Checks several dimensions at once. Checked in the order given, and a later failure does not
     * refund an earlier consumption: a rejected attempt is still an attempt, and refunding would
     * make failing on purpose the cheapest way to drain nothing.
     */
    public void requireAll(Dimension... dimensions) {
        for (Dimension dimension : dimensions) {
            require(dimension.policy(), dimension.value());
        }
    }

    public record Dimension(RateLimitPolicy policy, String value) {
    }

    /**
     * The caller's address. Client-influenced and therefore spoofable — which is exactly why the
     * IP limit is the loosest and never the only one applied.
     */
    public static String clientIp(HttpServletRequest request) {
        // Diverges from the donor: with server.forward-headers-strategy=framework, Spring's
        // ForwardedHeaderFilter has already applied X-Forwarded-For to getRemoteAddr() and HIDDEN
        // the header — reading it here would find nothing. Still client-influenced, which is why
        // the per-IP limits are the loose ones.
        return request.getRemoteAddr();
    }

    private static BucketConfiguration configurationFor(RateLimitPolicy policy, int capacity) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        // Interval rather than greedy refill: the whole allowance returns at the
                        // end of the window instead of trickling back, so "10 an hour" cannot be
                        // smoothed into a steady drip that runs all day.
                        .refillIntervally(capacity, policy.window())
                        .build())
                .build();
    }

    /** SHA-256, truncated. Enough to avoid collisions between keys; short enough to read in Redis. */
    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }

    private static String humanDelay(long seconds) {
        if (seconds < 60) {
            return seconds + " seconds";
        }
        long minutes = seconds / 60;
        return minutes < 60
                ? minutes + (minutes == 1 ? " minute" : " minutes")
                : (minutes / 60) + (minutes / 60 == 1 ? " hour" : " hours");
    }
}
