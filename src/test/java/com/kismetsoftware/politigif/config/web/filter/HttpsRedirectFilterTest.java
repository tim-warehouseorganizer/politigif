/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.config.web.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The plaintext-to-TLS redirect: 301 for GET (canonical, cacheable), 308 for everything else
 * (method-preserving), loopback exempt so dev needs no configuration, ACME exempt by definition.
 */
class HttpsRedirectFilterTest {

    @Test
    void aPlaintextGetIs301ToTheHttpsUrl() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/b/sugar-bean");
        request.setServerName("politigif.example");
        request.setSecure(false);
        request.setQueryString("tipped");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new HttpsRedirectFilter(true).doFilter(request, response, chain);

        assertEquals(301, response.getStatus());
        assertEquals("https://politigif.example/b/sugar-bean?tipped", response.getHeader("Location"));
        verify(chain, never()).doFilter(request, response);
    }

    /** A 301 downgrades POSTs to GETs in most clients; 308 keeps the method and body. */
    @Test
    void nonIdempotentMethodsGet308() {
        assertEquals(308, HttpsRedirectFilter.statusFor("POST"));
        assertEquals(308, HttpsRedirectFilter.statusFor("PUT"));
        assertEquals(301, HttpsRedirectFilter.statusFor("GET"));
        assertEquals(301, HttpsRedirectFilter.statusFor("HEAD"));
    }

    /** http://localhost is served normally — dev needs no configuration. */
    @Test
    void loopbackHostsAreNeverRedirected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setServerName("localhost");
        request.setSecure(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new HttpsRedirectFilter(true).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertTrue(HttpsRedirectFilter.isLoopback("127.0.0.1"));
        assertTrue(HttpsRedirectFilter.isLoopback("::1"));
        assertFalse(HttpsRedirectFilter.isLoopback("politigif.example"));
    }

    @Test
    void aSecureRequestPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setServerName("politigif.example");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new HttpsRedirectFilter(true).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    /** ACME HTTP-01 validation is defined to happen over plaintext. */
    @Test
    void acmeChallengesAreExempt() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/.well-known/acme-challenge/token123");
        request.setServerName("politigif.example");
        request.setSecure(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new HttpsRedirectFilter(true).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    /** The dyno port must never leak into the Location. */
    @Test
    void thePortIsDroppedFromTheHttpsUrl() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setServerName("politigif.example");
        request.setServerPort(34121);

        assertEquals("https://politigif.example/x", HttpsRedirectFilter.httpsUrl(request));
    }
}
