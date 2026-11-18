/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The shared perf playbook's fix: Security's blanket {@code no-store} would otherwise defeat static
 * caching entirely. Re-added scoped to dynamic pages only, so true statics keep the resource
 * handler's long-lived Cache-Control and are busted per deploy by the {@code ?v=} param.
 *
 * <p>The matcher list here has to stay in sync with the static-asset allowlist in
 * {@code SecurityConfig.securityPaths} — hence a test.
 */
class SecurityConfigCacheControlTest {

    @Test
    void dynamicPagesGetNoStore() {
        assertEquals("no-cache, no-store, max-age=0, must-revalidate", cacheControlFor("/"));
        assertEquals("no-cache, no-store, max-age=0, must-revalidate", cacheControlFor("/attention"));
        assertEquals("no-cache, no-store, max-age=0, must-revalidate",
                cacheControlFor("/businesses/3f1c"));
        assertEquals("no-cache, no-store, max-age=0, must-revalidate",
                cacheControlFor("/contacts"));
    }

    @Test
    void staticAssetsAreLeftAloneSoTheyCanActuallyBeCached() {
        assertNull(cacheControlFor("/css/custom.css"));
        assertNull(cacheControlFor("/js/anything.js"));
        assertNull(cacheControlFor("/fonts/poppins-400.woff2"));
        assertNull(cacheControlFor("/favicon.ico"));
        assertNull(cacheControlFor("/favicon.svg"));
        assertNull(cacheControlFor("/robots.txt"));
    }

    @Test
    void aVersionQueryParamDoesNotChangeTheDecision() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/css/custom.css");
        request.setQueryString("v=dev1234");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityConfig.dynamicPageCacheControlWriter().writeHeaders(request, response);

        assertNull(response.getHeader("Cache-Control"));
    }

    private static String cacheControlFor(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        HttpServletResponse response = new MockHttpServletResponse();

        HeaderWriter writer = SecurityConfig.dynamicPageCacheControlWriter();
        writer.writeHeaders(request, response);

        return ((MockHttpServletResponse) response).getHeader("Cache-Control");
    }
}
