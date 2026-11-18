/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.seedling;

import com.kismetsoftware.politigif.ratelimit.RateLimitPolicy;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import com.kismetsoftware.politigif.user.User;
import com.kismetsoftware.politigif.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/**
 * Planting, checking and changing a seedling. Every check is rate-limited per account and per IP
 * BEFORE BCrypt runs, and every attempt counts (not only wrong ones), so a stolen session cannot
 * be used to guess the seedling at speed. A forgotten seedling cannot be recovered: the way out is
 * the account reset.
 */
@Service
public class SeedlingService {

    static final String WRONG = "That seedling doesn't match.";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public SeedlingService(UserRepository users, PasswordEncoder encoder, RateLimiter rateLimiter, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * The first seedling on a new (or freshly reset) account. Refuses if one is already planted:
     * replacing a seedling always needs the old one ({@link #change}).
     *
     * @return the problem to show, or empty on success
     */
    public Optional<String> plant(String userId, String seedling, String confirmation) {
        User user = users.findById(userId).orElseThrow();
        if (user.hasSeedling()) {
            return Optional.of("This account already has a seedling. Change it from your account page.");
        }
        Optional<String> problem = SeedlingPolicy.problem(seedling, confirmation, user.getEmail());
        if (problem.isPresent()) {
            return problem;
        }
        store(user, seedling);
        return Optional.empty();
    }

    /**
     * Whether {@code candidate} is this account's seedling. Throws {@code RateLimitedException}
     * when over the per-account or per-IP limit. Use before ANY account settings change.
     */
    public boolean verify(String userId, String ip, String candidate) {
        rateLimiter.requireAll(
                new RateLimiter.Dimension(RateLimitPolicy.SEEDLING_PER_IP, ip),
                new RateLimiter.Dimension(RateLimitPolicy.SEEDLING_PER_USER, userId));
        User user = users.findById(userId).orElseThrow();
        return user.hasSeedling() && candidate != null && !candidate.isEmpty()
                && candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= SeedlingPolicy.MAX_BYTES
                && encoder.matches(candidate, user.getSeedlingHash());
    }

    /**
     * Changing the seedling is itself a settings change, so it needs the current one.
     *
     * @return the problem to show, or empty on success
     */
    public Optional<String> change(String userId, String ip, String current, String next, String confirmation) {
        if (!verify(userId, ip, current)) {
            return Optional.of(WRONG);
        }
        User user = users.findById(userId).orElseThrow();
        Optional<String> problem = SeedlingPolicy.problem(next, confirmation, user.getEmail());
        if (problem.isPresent()) {
            return problem;
        }
        store(user, next);
        return Optional.empty();
    }

    private void store(User user, String seedling) {
        user.setSeedlingHash(encoder.encode(seedling));
        user.setSeedlingChangedAt(clock.instant());
        users.save(user);
    }
}
