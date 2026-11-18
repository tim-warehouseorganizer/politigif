/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * One clock for the whole app, in the business's zone. "Today", "overdue" and "30 days ago" are
 * all wall-clock questions — asked in UTC, a follow-up due today would flip to overdue at 6pm in
 * Utah. Injected rather than {@code Instant.now()} so the attention rules are testable.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
