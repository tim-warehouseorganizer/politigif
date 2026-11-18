/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.ratelimit;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Buckets stored in the Redis we already run for sessions.
 *
 * <p>Distributed rather than in-process on purpose: an in-memory bucket limits one JVM, so the
 * limit silently multiplies by the instance count the first time this is scaled out — and the
 * moment it needs limiting is the moment it has more than one instance.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimiterConfig {

    @Bean
    public ProxyManager<String> rateLimitBuckets(RedissonClient sessionRedissonClient) {
        CommandAsyncExecutor executor = ((Redisson) sessionRedissonClient).getCommandExecutor();
        // Compare-and-swap rather than Lua: no server-side script to ship, and the contention this
        // sees (a handful of writers per key) is well inside where CAS is the cheaper choice.
        return Bucket4jRedisson.casBasedBuilder(executor).build();
    }
}
