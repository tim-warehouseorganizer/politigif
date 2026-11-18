/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.config.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Redirects plaintext HTTP requests to the same URL over HTTPS.
 *
 * <p>Behind a TLS-terminating router the app itself always speaks plain HTTP, so nothing in the
 * stack objects when a visitor arrives over {@code http://} — the site simply answers 200 and the
 * page, the session cookie (which loses its {@code Secure} flag on a non-secure request) and
 * anything posted back all travel in the clear. HSTS does not rescue that case: browsers ignore
 * {@code Strict-Transport-Security} on a plaintext response, so a first-time HTTP visitor never
 * learns the site is HTTPS-only. This redirect is what puts them on TLS, and therefore what makes
 * the HSTS header in SecurityConfig reachable at all.
 *
 * <p><b>301, not 302.</b> A permanent redirect is cached by the browser and tells crawlers the
 * HTTPS URL is the canonical one; a 302 says the HTTP URL is real and this is a temporary detour,
 * which splits indexing across two origins. Spring Security's
 * {@code requiresChannel().requiresSecure()} emits a 302, which is why this is a filter instead.
 *
 * <p>Non-idempotent methods get a <b>308</b>: browsers and most HTTP clients downgrade a 301 to a
 * GET, so an API client mistakenly pointed at {@code http://} would arrive without its body and
 * fail like a routing bug. 308 preserves the method. Either way that request has already leaked
 * and the sender needs re-pointing at HTTPS.
 *
 * <p><b>The scheme comes from {@link HttpServletRequest#isSecure()}, not from reading
 * {@code X-Forwarded-Proto} here.</b> With {@code server.forward-headers-strategy=framework}
 * Spring's ForwardedHeaderFilter runs at {@code HIGHEST_PRECEDENCE}, applies the forwarded headers,
 * and then <em>hides</em> them from everything downstream — a filter that read the header itself
 * would see nothing and never redirect. {@code isSecure()} is also the same signal Spring
 * Security's HSTS writer and the Secure-cookie decision use, so the whole app agrees on one answer.
 *
 * <p>Development needs no configuration: loopback hosts are skipped, so {@code http://localhost}
 * is served normally. {@code app.security.https-redirect.enabled=false} is the escape hatch if a
 * production redirect ever has to come off without a code change.
 */
public class HttpsRedirectFilter extends OncePerRequestFilter {

    /** ACME HTTP-01 validation is defined to happen over plaintext — never redirect it. */
    static final String ACME_CHALLENGE_PREFIX = "/.well-known/acme-challenge/";

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "0:0:0:0:0:0:0:1", "::1");

    private final boolean enabled;

    public HttpsRedirectFilter(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!shouldRedirect(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(statusFor(request.getMethod()));
        response.setHeader("Location", httpsUrl(request));
        response.setContentLength(0);
    }

    private boolean shouldRedirect(HttpServletRequest request) {
        return enabled
                && !request.isSecure()
                && !isLoopback(request.getServerName())
                && !request.getRequestURI().startsWith(ACME_CHALLENGE_PREFIX);
    }

    static boolean isLoopback(String serverName) {
        return serverName != null && LOOPBACK_HOSTS.contains(serverName.toLowerCase(java.util.Locale.ROOT));
    }

    static int statusFor(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                ? HttpServletResponse.SC_MOVED_PERMANENTLY
                : 308;
    }

    /**
     * The public host, port deliberately dropped: behind the router the app listens on an arbitrary
     * dyno port, and carrying it into the Location would send the visitor somewhere that isn't
     * listening. getServerName() is already proxy-corrected by ForwardedHeaderFilter.
     */
    static String httpsUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder("https://")
                .append(request.getServerName())
                .append(request.getRequestURI());
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            url.append('?').append(query);
        }
        return url.toString();
    }
}
