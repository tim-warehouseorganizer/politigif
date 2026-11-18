/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.config.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Serves minified JS/CSS without touching the templates — ported from the sibling apps. Templates
 * reference the readable filenames; this resolver, active everywhere except the {@code dev}
 * profile, transparently returns the {@code .min} twin when one exists (the IntelliJ obfuscation
 * watchers generate them on save). When a minified twin is absent it falls through to the original
 * file.
 */
/*
 * Gated "not dev" rather than "only prod" on purpose.
 *
 * As @Profile("prod") this was opt-IN through an ambient environment variable, and two of the five
 * sibling apps never had it set - so they served readable, un-obfuscated source in production for
 * as long as anyone had been deploying them. Nothing failed: the resolver's entire design goal is
 * that nothing else changes when it is active, so its absence is equally invisible. The only
 * symptom is that the bytes on the wire are the ones in the repo.
 *
 * A rule whose failure mode is "ship your source" belongs on by default. Debugging against readable
 * files is still one flag away - run with --spring.profiles.active=dev.
 */
@Configuration
@Profile("!dev")
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/js/**", "/css/**")
                .addResourceLocations("classpath:/static/js/", "classpath:/static/css/")
                // A CUSTOM handler does not inherit spring.web.resources.cache.* — without this,
                // prod js/css ship with no Cache-Control at all. Keep in sync with the 30d in
                // application.properties; the ?v= deploy param (StaticVersionAdvice) busts staleness.
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .resourceChain(true)
                .addResolver(new MinifiedFirstResourceResolver());
    }

    /**
     * Serves {@code foo.min.js} / {@code foo.min.css} when a request asks for {@code foo.js} /
     * {@code foo.css} and the minified twin exists, falling back to the original when it does not.
     *
     * <p>Nothing in the templates references a minified filename — they all ask for the readable
     * one — so this resolver IS the minification. If it stopped matching, every page would keep
     * working and every asset would quietly be unminified: a performance regression with no error
     * attached to it. Hence {@code MinifiedAssetResolverTest}.
     *
     * <p>Named rather than anonymous so a test can construct it without reaching into Spring's
     * registry internals.
     */
    static class MinifiedFirstResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource minified = minifiedTwin(resourcePath, location);
            return minified != null ? minified : super.getResource(resourcePath, location);
        }

        /**
         * The {@code .min.js}/{@code .min.css} sibling of the request, if one exists and is
         * readable. Suffix-safe on purpose: {@code String.replace} rewrites every occurrence, so a
         * path like {@code chart.js/plugin.js} would become {@code chart.min.js/plugin.min.js} and
         * silently miss. Every asset is a flat file today, which is exactly why that would go
         * unnoticed until someone adds a subdirectory.
         */
        static String minifiedPathFor(String resourcePath) {
            if (resourcePath.endsWith(".js") && !resourcePath.endsWith(".min.js")) {
                return resourcePath.substring(0, resourcePath.length() - ".js".length()) + ".min.js";
            }
            if (resourcePath.endsWith(".css") && !resourcePath.endsWith(".min.css")) {
                return resourcePath.substring(0, resourcePath.length() - ".css".length()) + ".min.css";
            }
            return null;
        }

        Resource minifiedTwin(String resourcePath, Resource location) throws IOException {
            String minifiedPath = minifiedPathFor(resourcePath);
            if (minifiedPath == null) {
                return null;
            }
            Resource minified = location.createRelative(minifiedPath);
            return minified.exists() && minified.isReadable() ? minified : null;
        }
    }
}
