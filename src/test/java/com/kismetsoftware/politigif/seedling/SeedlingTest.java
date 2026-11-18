/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.seedling;

import com.kismetsoftware.politigif.ratelimit.RateLimitPolicy;
import com.kismetsoftware.politigif.ratelimit.RateLimitedException;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import com.kismetsoftware.politigif.user.User;
import com.kismetsoftware.politigif.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeedlingTest {

    private static final String EMAIL = "pat@example.com";
    private static final String IP = "203.0.113.7";
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    // ---- SeedlingPolicy ----

    @Test
    void aGoodSeedlingHasNoProblem() {
        assertThat(SeedlingPolicy.problem("tomato-vine-42", "tomato-vine-42", EMAIL)).isEmpty();
    }

    @Test
    void aBlankSeedlingIsRefused() {
        assertThat(SeedlingPolicy.problem("   ", "   ", EMAIL)).contains("Choose a seedling.");
        assertThat(SeedlingPolicy.problem(null, null, EMAIL)).isPresent();
    }

    @Test
    void sevenCharactersIsTooShortAndEightIsEnough() {
        assertThat(SeedlingPolicy.problem("1234567", "1234567", EMAIL)).get().asString().contains("at least 8");
        assertThat(SeedlingPolicy.problem("12345678", "12345678", EMAIL)).isEmpty();
    }

    @Test
    void lengthCountsCharactersNotUtf16Units() {
        // Four emoji are 8 UTF-16 units but 4 characters.
        String four = "🌱🌱🌱🌱";
        assertThat(SeedlingPolicy.problem(four, four, EMAIL)).isPresent();
    }

    @Test
    void over72BytesIsRefused() {
        String ok = "a".repeat(72);
        String tooLong = "a".repeat(73);
        String multiByte = "é".repeat(37); // 74 bytes, 37 characters

        assertThat(SeedlingPolicy.problem(ok, ok, EMAIL)).isEmpty();
        assertThat(SeedlingPolicy.problem(tooLong, tooLong, EMAIL)).get().asString().contains("too long");
        assertThat(SeedlingPolicy.problem(multiByte, multiByte, EMAIL)).get().asString().contains("too long");
    }

    @Test
    void theTwoEntriesMustMatch() {
        assertThat(SeedlingPolicy.problem("tomato-vine-42", "tomato-vine-43", EMAIL)).contains("The two seedlings don't match.");
    }

    @Test
    void theEmailAddressIsNotASeedling() {
        assertThat(SeedlingPolicy.problem("Pat@Example.com", "Pat@Example.com", EMAIL)).isPresent();
    }

    // ---- SeedlingService ----

    private UserRepository users;
    private RateLimiter rateLimiter;
    private PasswordEncoder encoder;
    private SeedlingService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        rateLimiter = mock(RateLimiter.class);
        encoder = new BCryptPasswordEncoder(4);
        service = new SeedlingService(users, encoder, rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User(EMAIL, NOW);
        user.setId("u1");
        when(users.findById("u1")).thenReturn(Optional.of(user));
    }

    @Test
    void plantingStoresABcryptHashNeverTheSeedling() {
        assertThat(service.plant("u1", "tomato-vine-42", "tomato-vine-42")).isEmpty();

        assertThat(user.getSeedlingHash()).startsWith("$2").doesNotContain("tomato");
        assertThat(encoder.matches("tomato-vine-42", user.getSeedlingHash())).isTrue();
        assertThat(user.getSeedlingChangedAt()).isEqualTo(NOW);
        verify(users).save(user);
    }

    @Test
    void plantingRefusesAWeakSeedling() {
        assertThat(service.plant("u1", "short", "short")).isPresent();
        verify(users, never()).save(any());
    }

    @Test
    void plantingTwiceIsRefusedBecauseChangingNeedsTheOldOne() {
        user.setSeedlingHash(encoder.encode("tomato-vine-42"));

        assertThat(service.plant("u1", "another-one-9", "another-one-9")).isPresent();
        verify(users, never()).save(any());
    }

    @Test
    void verifyAcceptsTheRightSeedling() {
        user.setSeedlingHash(encoder.encode("tomato-vine-42"));

        assertThat(service.verify("u1", IP, "tomato-vine-42")).isTrue();
    }

    @Test
    void verifyRefusesAWrongSeedling() {
        user.setSeedlingHash(encoder.encode("tomato-vine-42"));

        assertThat(service.verify("u1", IP, "tomato-vine-43")).isFalse();
    }

    @Test
    void verifyRefusesWhenNoSeedlingIsPlanted() {
        assertThat(service.verify("u1", IP, "anything-at-all")).isFalse();
    }

    @Test
    void verifyRefusesAnOverlongGuessWithoutHashing() {
        user.setSeedlingHash(encoder.encode("tomato-vine-42"));

        assertThat(service.verify("u1", IP, "a".repeat(200))).isFalse();
    }

    @Test
    void everyGuessIsCountedPerIpAndPerAccount() {
        user.setSeedlingHash(encoder.encode("tomato-vine-42"));

        service.verify("u1", IP, "tomato-vine-42");

        verify(rateLimiter).requireAll(
                new RateLimiter.Dimension(RateLimitPolicy.SEEDLING_PER_IP, IP),
                new RateLimiter.Dimension(RateLimitPolicy.SEEDLING_PER_USER, "u1"));
    }

    @Test
    void aRateLimitedGuessNeverReachesBcrypt() {
        doThrow(new RateLimitedException("slow", 60)).when(rateLimiter).requireAll(any(), any());

        assertThatThrownBy(() -> service.verify("u1", IP, "tomato-vine-42")).isInstanceOf(RateLimitedException.class);
        verify(users, never()).findById(any());
    }

    @Test
    void changingNeedsTheCurrentSeedling() {
        user.setSeedlingHash(encoder.encode("tomato-vine-42"));
        String before = user.getSeedlingHash();

        assertThat(service.change("u1", IP, "wrong-guess-1", "new-seedling-7", "new-seedling-7"))
                .contains(SeedlingService.WRONG);
        assertThat(user.getSeedlingHash()).isEqualTo(before);
    }

    @Test
    void changingAppliesThePolicyToTheNewSeedling() {
        user.setSeedlingHash(encoder.encode("tomato-vine-42"));

        assertThat(service.change("u1", IP, "tomato-vine-42", "new-seedling-7", "new-seedling-8")).isPresent();
        verify(users, never()).save(any());
    }

    @Test
    void changingWithTheRightSeedlingReplacesIt() {
        user.setSeedlingHash(encoder.encode("tomato-vine-42"));

        assertThat(service.change("u1", IP, "tomato-vine-42", "new-seedling-7", "new-seedling-7")).isEmpty();
        assertThat(encoder.matches("new-seedling-7", user.getSeedlingHash())).isTrue();
    }
}
