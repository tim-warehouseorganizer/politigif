/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.security;

import com.kismetsoftware.politigif.user.User;
import com.kismetsoftware.politigif.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Re-checks the account behind an existing session on every request (fieldbook donor shape):
 * <ul>
 *   <li>account gone (reset or deleted) → the session ends, back to {@code /signin?ended};</li>
 *   <li>no seedling yet → held at {@code /seedling} until one is chosen.</li>
 * </ul>
 * Spring Security never consults the store again once a session exists, so without this an
 * account reset would leave the account's other sessions signed in to an account that no longer
 * exists.
 */
public class AccountStatusFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusFilter.class);

    private final UserRepository userRepository;
    private final SessionSignIn sessionSignIn;

    public AccountStatusFilter(UserRepository userRepository, SessionSignIn sessionSignIn) {
        this.userRepository = userRepository;
        this.sessionSignIn = sessionSignIn;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/fonts/")
                || path.equals("/favicon.ico") || path.equals("/favicon.svg") || path.equals("/robots.txt")
                || path.startsWith("/.well-known/") || path.equals("/error") || path.equals("/signout")
                || path.equals("/signin") || path.startsWith("/signin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof PolitigifPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }
        User current = userRepository.findById(principal.userId()).orElse(null);
        if (current == null) {
            log.info("ending session for an account that no longer exists ({})", principal.userId());
            sessionSignIn.signOut(request);
            response.sendRedirect("/signin?ended");
            return;
        }
        if (!current.hasSeedling() && !request.getRequestURI().equals("/seedling")) {
            response.sendRedirect("/seedling");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
