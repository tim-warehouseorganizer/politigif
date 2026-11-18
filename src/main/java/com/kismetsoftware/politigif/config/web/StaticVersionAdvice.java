/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.config.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the deploy version to every rendered view as {@code ${staticVersion}}, appended to static
 * asset URLs as a {@code ?v=} query param. Static responses are cached for 30 days (SecurityConfig's
 * cache-control exemption + spring.web.resources.cache.* / WebConfig outside dev), so the version
 * bump on each deploy is what busts browser caches. Ported unchanged from the sibling apps —
 * query-param versioning by explicit preference, NOT filename fingerprinting.
 */
@ControllerAdvice
public class StaticVersionAdvice {

    private static final Logger LOG = LoggerFactory.getLogger(StaticVersionAdvice.class);

    /** The value a deploy has not overridden. */
    private static final String DEV_PLACEHOLDER = "local";

    private final String staticVersion;

    public StaticVersionAdvice(@Value("${app.static.version}") String staticVersion) {
        this.staticVersion = resolve(staticVersion);
    }

    /**
     * A BLANK configured version is the dangerous case, and the reason this is not just a field
     * assignment. {@code heroku config:set APP_STATIC_VERSION=$(...)} with a subshell that produced
     * nothing sets the variable to the empty string — which is present, so it beats every fallback,
     * and every asset then renders as {@code ?v=} with a 30-day max-age behind it. That token never
     * changes, so the NEXT deploy's css and js never reach anyone who has already visited: strictly
     * worse than having no override at all, and invisible unless someone reads the page source.
     * Blank is therefore treated as absent.
     *
     * <p>In development the configured value is the literal "local" and never changes, while static
     * responses carry a 30-day max-age — so every css and js edit would be invisible until someone
     * thought to hard-refresh. Substituting the JVM's start time makes a restart bust the cache,
     * which is what a developer already expects a restart to do.
     */
    static String resolve(String configured) {
        if (configured != null && !configured.isBlank() && !DEV_PLACEHOLDER.equals(configured)) {
            return configured;
        }
        if (configured != null && configured.isBlank()) {
            LOG.warn("app.static.version is blank — falling back to the JVM start time. In production "
                    + "set APP_STATIC_VERSION to a real per-release value, or enable Heroku's "
                    + "runtime-dyno-metadata labs feature so HEROKU_RELEASE_VERSION is populated.");
        }
        return "dev" + java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    @ModelAttribute("staticVersion")
    public String staticVersion() {
        return staticVersion;
    }
}
