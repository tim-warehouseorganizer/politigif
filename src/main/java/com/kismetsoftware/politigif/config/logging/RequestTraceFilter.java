/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.config.logging;

import com.kismetsoftware.politigif.security.PolitigifPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trace context for every request, ported from the grubdub/plainsight donors (the
 * taxdocscanner / warehouseorganizer lineage): trace/request ids into the MDC and echoed as
 * response headers, the signed-in user, entity uuids from the URI path, and one INFO line per
 * request timed around the whole downstream chain — including Thymeleaf render, which
 * controller-scoped timing misses and which is often the dominant cost on small dynos.
 *
 * <p>Registered on both filter chains, so API requests from the mobile apps carry the same trace
 * vocabulary as web requests.
 */
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

    public static final String TRACE_HEADER = "X-PG-Trace-Id";
    public static final String REQUEST_HEADER = "X-PG-Request-Id";

    /** What an acceptable caller-supplied id looks like: UUID-ish, bounded, no log-injection room. */
    private static final Pattern SAFE_ID = Pattern.compile("^[a-zA-Z0-9\\-]{8,36}$");

    private static final Pattern GIF_ID_PATTERN = Pattern.compile(
            "/g/([A-Za-z0-9]{10})\\b");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/fonts/")
                || path.equals("/favicon.ico") || path.equals("/favicon.svg")
                || path.startsWith("/.well-known/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = idFrom(request, TRACE_HEADER);
        String requestId = idFrom(request, REQUEST_HEADER);
        // Echoed unconditionally: the id a user quotes from an error page must match our logs even
        // when the request then fails deep in the chain.
        response.setHeader(TRACE_HEADER, traceId);
        response.setHeader(REQUEST_HEADER, requestId);

        long startNanos = System.nanoTime();
        try {
            MDC.put(MdcKeys.TRACE, traceId);
            MDC.put(MdcKeys.REQUEST, requestId);
            MDC.put(MdcKeys.URI, request.getRequestURI());
            MDC.put(MdcKeys.HTTP_METHOD, request.getMethod());
            MDC.put(MdcKeys.CURRENT_USER, currentUser());
            Matcher matcher = GIF_ID_PATTERN.matcher(request.getRequestURI());
            if (matcher.find()) {
                MDC.put(MdcKeys.GIF_ID, matcher.group(1));
            }
            filterChain.doFilter(request, response);
        } finally {
            if (MDC.get(MdcKeys.TRACE) != null) {
                // Locale.ROOT deliberately: a locale-sensitive format can emit a grouping comma
                // ("1,234") that breaks JSON parsing downstream — same fix as the donor.
                log.info("{\"c\":\"RequestTraceFilter\",\"m\":\"complete\",\"d\":\"{}\",\"sc\":\"{}\"}",
                        String.format(Locale.ROOT, "%.3fms", (System.nanoTime() - startNanos) / 1_000_000.0),
                        response.getStatus());
            }
            MDC.clear();
        }
    }

    private static String idFrom(HttpServletRequest request, String header) {
        String supplied = request.getHeader(header);
        return supplied != null && SAFE_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof PolitigifPrincipal principal) {
            return principal.userId();
        }
        return "ANONYMOUS";
    }
}
