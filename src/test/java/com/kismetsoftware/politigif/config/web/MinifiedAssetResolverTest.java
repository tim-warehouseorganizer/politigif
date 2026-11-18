/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.config.web;

import com.kismetsoftware.politigif.config.web.WebConfig.MinifiedFirstResourceResolver;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the minified-first swap in {@link WebConfig}. Nothing in the templates asks for a minified
 * filename, so this resolver IS the minification — if it silently stopped matching, every page
 * would keep working and every asset would quietly be unminified: a regression with no error
 * attached. And the profile polarity is asserted literally, because {@code @Profile("prod")} was
 * exactly how two sibling apps shipped readable source to production without anything failing.
 */
class MinifiedAssetResolverTest {

    private static final Resource CSS = new ClassPathResource("static/css/");

    private final MinifiedFirstResourceResolver resolver = new MinifiedFirstResourceResolver();

    @Test
    void aCssRequestResolvesToTheMinifiedSibling() throws Exception {
        Resource resource = resolver.minifiedTwin("site.css", CSS);

        assertNotNull(resource, "site.min.css is missing — regenerate the minified twin");
        assertEquals("site.min.css", resource.getFilename());
        assertTrue(resource.exists() && resource.isReadable());
    }

    @Test
    void askingForTheMinifiedNameDoesNotDoubleMinify() throws Exception {
        // site.min.css must not become site.min.min.css.
        assertNull(MinifiedFirstResourceResolver.minifiedPathFor("site.min.css"));
        assertNull(MinifiedFirstResourceResolver.minifiedPathFor("app.min.js"));
        assertNull(resolver.minifiedTwin("site.min.css", CSS));
    }

    @Test
    void anAssetWithNoMinifiedTwinYieldsNullSoTheOriginalIsServed() throws Exception {
        // Returning null is what makes getResource() fall through to super — i.e. serve the
        // original rather than 404.
        assertNull(resolver.minifiedTwin("no-such-stylesheet.css", CSS));
    }

    @Test
    void nonAssetPathsAreLeftAlone() {
        assertNull(MinifiedFirstResourceResolver.minifiedPathFor("favicon.svg"));
        assertNull(MinifiedFirstResourceResolver.minifiedPathFor("robots.txt"));
    }

    /**
     * The suffix rule. {@code String.replace} — which two sibling apps used until 2026-08-23 —
     * rewrites every occurrence, so a path with ".js" or ".css" earlier in it gets mangled in the
     * middle.
     */
    @Test
    void theMinifiedNameComesFromTheSuffixNotEveryOccurrence() {
        assertEquals("chart.js/plugin.min.js",
                MinifiedFirstResourceResolver.minifiedPathFor("chart.js/plugin.js"));
        assertEquals("theme.css/print.min.css",
                MinifiedFirstResourceResolver.minifiedPathFor("theme.css/print.css"));
    }

    /**
     * The polarity that shipped source to production. As {@code @Profile("prod")} the resolver was
     * opt-IN via an ambient env var nobody set; nothing failed, because its absence is invisible
     * by design. {@code @Profile("!dev")} makes the safe behavior the default.
     */
    @Test
    void theProfileIsNotDevNotOnlyProd() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/kismetsoftware/politigif/config/web/WebConfig.java"));

        assertTrue(source.contains("@Profile(\"!dev\")"),
                "WebConfig must be on-by-default (!dev), never opt-in (prod)");
    }
}
