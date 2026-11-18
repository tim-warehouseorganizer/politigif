/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.Kryo5Codec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Redis-backed sessions over Redisson — the family pattern, with WHO's 2026-08-28 namespace fix in
 * from day one: the key namespace is applied by {@link #sessionKeyNamespaceCustomizer} rather than
 * a {@code redisNamespace} attribute, which was observed not to apply on Boot 4.
 */
@Configuration
@EnableRedisHttpSession
public class RedissonSessionConfig {

    /**
     * A per-app cookie name: browser cookies are scoped by host, not port, so every Spring app on
     * {@code localhost} that keeps the default "SESSION" clobbers the others' logins.
     */
    @Bean
    public CookieSerializer cookieSerializer(@Value("${app.redis.key-prefix:politigif}") String appName) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(appName.toUpperCase(java.util.Locale.ROOT) + "SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean(name = "sessionRedissonClient")
    @Primary
    public RedissonClient sessionRedissonClient(
            @Value("${redis.session.url}") String url,
            @Value("${redis.session.password}") String password) {
        Config config = new Config();
        config.setCodec(new Kryo5Codec());
        SingleServerConfig single = config.useSingleServer().setAddress(url);
        // Redisson treats an empty string as a real (wrong) password; only set a configured one.
        if (password != null && !password.isBlank()) {
            single.setPassword(password);
        }
        return Redisson.create(config);
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory(RedissonClient sessionRedissonClient) {
        return new RedissonConnectionFactory(sessionRedissonClient);
    }

    /** Runs after construction, so it wins whichever of the annotation or Boot auto-config built the repository. */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SessionRepositoryCustomizer<RedisSessionRepository> sessionKeyNamespaceCustomizer(
            @Value("${app.redis.key-prefix:politigif}") String keyPrefix,
            @Value("${app.session.idle-timeout:12h}") java.time.Duration idleTimeout) {
        // The idle timeout is set here too: with @EnableRedisHttpSession, server.servlet.session.timeout
        // is ignored and sessions expire after the annotation's 30-minute default — too short for
        // someone who opens the app between visits all day.
        return repository -> {
            repository.setRedisKeyNamespace(keyPrefix + ":session");
            repository.setDefaultMaxInactiveInterval(idleTimeout);
        };
    }
}
