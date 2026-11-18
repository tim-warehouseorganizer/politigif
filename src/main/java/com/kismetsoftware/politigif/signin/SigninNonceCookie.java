/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.signin;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The browser-binding cookie for sign-in links: HttpOnly, Secure (off only for local http dev),
 * SameSite=Lax, scoped to {@code /signin}. Lax, not Strict, because the link is opened from a mail
 * client (a cross-site top-level GET) and a Strict cookie would not arrive, so every link would
 * look like it was opened in the wrong browser.
 */
@Component
public class SigninNonceCookie {

    public static final String NAME = "PG_SIGNIN";

    private final boolean secure;
    private final Duration maxAge;

    public SigninNonceCookie(@Value("${app.signin.secure-cookie:true}") boolean secure,
                             @Value("${app.signin.link-validity:15m}") Duration validity) {
        this.secure = secure;
        // Outlives the link a little, so a link clicked at minute 14 still finds its cookie.
        this.maxAge = validity.plusMinutes(5);
    }

    /** The browser's current nonce, or null. */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && Tokens.isNonce(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Reuses the browser's nonce when it has one (so two links asked for in a row both work in
     * it), otherwise mints one; writes the cookie either way to refresh its lifetime.
     */
    public String ensure(HttpServletRequest request, HttpServletResponse response) {
        String nonce = read(request);
        if (nonce == null) {
            nonce = Tokens.newNonce();
        }
        response.addHeader("Set-Cookie", ResponseCookie.from(NAME, nonce)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/signin")
                .maxAge(maxAge)
                .build()
                .toString());
        return nonce;
    }
}
