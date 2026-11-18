/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every file shipped under {@code static/} must be reachable without a session.
 *
 * <p>{@link SecurityConfig} ends in {@code anyRequest().denyAll()}, which is the right default and
 * has one sharp edge: adding an asset directory is not enough to serve it. A path nobody listed
 * returns 403, and a 403 on a subresource leaves no trace in the log of the page that requested
 * it — the page returns 200, the browser quietly falls back, and the only symptom is that the
 * site looks slightly wrong to everyone except whoever has it cached. The sibling apps shipped
 * fonts as 403s for a day this way.
 *
 * <p>So this walks the real directory rather than checking a list of paths someone remembered to
 * write down. A new asset folder fails the build the day it is added, not the day it is noticed.
 *
 * <p>Note: {@code data/} (the OSM seed bundle) lives under {@code resources/}, not
 * {@code resources/static/}, precisely so it is NOT served — it's input, not an asset.
 */
class StaticAssetsReachableTest {

    private static final Path STATIC = Path.of("src/main/resources/static");
    private static final Path CONFIG =
            Path.of("src/main/java/com/kismetsoftware/politigif/security/SecurityConfig.java");

    /**
     * The GET patterns the config opens to everyone. Kept here as well as in the config so that
     * widening the config is a deliberate two-file act rather than a reflex while chasing a 403 —
     * this list is the one a reviewer reads.
     */
    private static final List<String> PUBLIC_PATTERNS = List.of(
            "/css/**",
            "/js/**",
            "/.well-known/**",
            "/fonts/**",
            "/favicon.ico",
            "/favicon.svg",
            "/robots.txt");

    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    @Test
    void everyStaticFileIsCoveredByAPermitAllPattern() throws IOException {
        List<PathPattern> patterns = PUBLIC_PATTERNS.stream().map(PARSER::parse).toList();
        List<String> unreachable = new ArrayList<>();

        try (Stream<Path> files = Files.walk(STATIC)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String url = "/" + STATIC.relativize(file).toString().replace('\\', '/');
                PathContainer parsed = PathContainer.parsePath(url);
                if (patterns.stream().noneMatch(p -> p.matches(parsed))) {
                    unreachable.add(url);
                }
            }
        }

        assertTrue(unreachable.isEmpty(),
                "These files are served from static/ but are not public per SecurityConfig, so the "
                        + "browser cannot load them and the page that needs them degrades silently:\n  "
                        + String.join("\n  ", unreachable));
    }

    @Test
    void theConfigActuallyGrantsEveryPatternListedHere() throws IOException {
        String config = Files.readString(CONFIG);
        List<String> missing = PUBLIC_PATTERNS.stream()
                .filter(pattern -> !config.contains('"' + pattern + '"'))
                .toList();

        assertTrue(missing.isEmpty(),
                "This test would pass while the app fails: the patterns below are asserted here but "
                        + "no longer appear in SecurityConfig:\n  " + String.join("\n  ", missing));
    }

    @Test
    void staticAssetsAreExemptFromTheNoStoreCacheHeader() throws IOException {
        // Security's default Cache-Control is no-store, which is correct for pages carrying
        // session data and wasteful for a stylesheet. The exemption list lives in
        // staticAssetMatcher and has to be widened alongside the permit list — an asset that is
        // reachable but uncacheable is re-fetched on every navigation.
        String config = Files.readString(CONFIG);
        int start = config.indexOf("RequestMatcher staticAssetMatcher");
        assertTrue(start > 0, "the static-asset matcher has been renamed or removed");
        String body = config.substring(start, config.indexOf("\n    }", start));

        assertTrue(body.contains("\"/css/**\""), "css permitted but still no-store");
        assertTrue(body.contains("\"/robots.txt\""), "robots.txt permitted but still no-store");
    }
}
