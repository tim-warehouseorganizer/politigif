/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.security;

import com.kismetsoftware.politigif.signin.AdminEmails;
import com.kismetsoftware.politigif.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts a user into the session after a redeemed sign-in link, and takes them out again. There is
 * no form login in this app, so this is the only place an Authentication is created.
 */
@Component
public class SessionSignIn {

    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();
    private final AdminEmails adminEmails;

    public SessionSignIn(AdminEmails adminEmails) {
        this.adminEmails = adminEmails;
    }

    public void signIn(User user, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        // A new session id at the privilege change: a planted pre-sign-in id is worthless.
        request.changeSessionId();
        boolean admin = adminEmails.isAdmin(user.getEmail());
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (admin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new PolitigifPrincipal(user.getId(), user.getEmail(), admin), null, authorities));
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
    }

    public void signOut(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
