/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Resolves the CSRF token before any view renders, on every page (fieldbook donor).
 *
 * <p>Spring Security defers the token until a form reads it, and for a visitor with no session
 * reading it creates the session. When that happens mid-render, after Tomcat has committed the
 * response (here at 2KB, {@code server.compression.min-response-size}), the session can't be
 * created and the page just ends: status 200, no error. Share pages carry a report form, so this
 * matters for signed-out visitors here.
 */
@ControllerAdvice
public class CsrfEagerAdvice {

    @ModelAttribute
    public void loadCsrfToken(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrf) {
            csrf.getToken();
        }
    }
}
