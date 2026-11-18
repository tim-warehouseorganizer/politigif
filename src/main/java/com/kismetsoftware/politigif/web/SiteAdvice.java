/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * {@code ${canonicalBaseUrl}} for canonical links: the same property the sitemap and the share
 * pages' og: tags are built from, so they can never disagree.
 */
@ControllerAdvice
public class SiteAdvice {

    private final String canonicalBaseUrl;

    public SiteAdvice(@Value("${app.canonical-base-url}") String canonicalBaseUrl) {
        this.canonicalBaseUrl = canonicalBaseUrl.endsWith("/")
                ? canonicalBaseUrl.substring(0, canonicalBaseUrl.length() - 1) : canonicalBaseUrl;
    }

    @ModelAttribute("canonicalBaseUrl")
    public String canonicalBaseUrl() {
        return canonicalBaseUrl;
    }
}
