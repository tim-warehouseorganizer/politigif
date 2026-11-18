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
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Emits the app's Content-Security-Policy with a fresh per-request nonce. Ported from the
 * fieldbook donor (plainsight lineage) with the policy trimmed to what Politigif uses: no
 * third-party hosts at all, plus {@code media-src 'self'} for the share-page video.
 *
 * <p>The nonce is stashed as a request attribute ({@link #NONCE_ATTRIBUTE}) that
 * {@code CspNonceAdvice} surfaces to Thymeleaf as {@code ${cspNonce}}. This filter OWNS the CSP
 * header — SecurityConfig's headers block must not also set it, or the two would conflict.
 */
public class CspNonceFilter extends OncePerRequestFilter {

    public static final String NONCE_ATTRIBUTE = "cspNonce";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64 = Base64.getEncoder();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String nonce = (String) request.getAttribute(NONCE_ATTRIBUTE);
        if (nonce == null) {
            byte[] bytes = new byte[16];
            SECURE_RANDOM.nextBytes(bytes);
            nonce = BASE64.encodeToString(bytes);
            request.setAttribute(NONCE_ATTRIBUTE, nonce);
        }
        response.setHeader("Content-Security-Policy", buildPolicy(nonce));
        filterChain.doFilter(request, response);
    }

    /** Run on the ERROR dispatch too so error views get the header and a nonce. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    static String buildPolicy(String nonce) {
        return "default-src 'self'; "
                + "script-src 'self' 'nonce-" + nonce + "'; "
                + "style-src 'self' 'unsafe-inline'; "
                // data: for the inline SVG favicon.
                + "img-src 'self' data:; "
                // The share page plays the stored MP4 in a <video>.
                + "media-src 'self'; "
                + "font-src 'self'; "
                + "connect-src 'self'; "
                + "form-action 'self'; "
                + "base-uri 'none'; "
                + "object-src 'none'; "
                + "frame-ancestors 'none'";
    }
}
