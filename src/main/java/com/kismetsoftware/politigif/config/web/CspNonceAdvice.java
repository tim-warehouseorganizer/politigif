/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.config.web;

import com.kismetsoftware.politigif.config.web.filter.CspNonceFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the per-request CSP nonce (set by {@link CspNonceFilter}) to every rendered view as
 * {@code ${cspNonce}}, so inline JSON-LD blocks can carry {@code th:attr="nonce=${cspNonce}"}
 * and satisfy the nonce-based {@code script-src}. Applies to all controllers, so full pages,
 * fragments, and error views rendered through Spring MVC all receive it.
 */
@ControllerAdvice
public class CspNonceAdvice {

    @ModelAttribute("cspNonce")
    public String cspNonce(HttpServletRequest request) {
        Object nonce = request.getAttribute(CspNonceFilter.NONCE_ATTRIBUTE);
        return nonce == null ? "" : nonce.toString();
    }
}
