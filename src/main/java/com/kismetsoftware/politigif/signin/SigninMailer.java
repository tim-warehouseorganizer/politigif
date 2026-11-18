/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.signin;

import com.kismetsoftware.politigif.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Delivers a sign-in link: by mail (Resend's SMTP relay) when configured, otherwise, in local dev
 * only ({@code app.signin.log-links=true}), into the log.
 */
@Component
public class SigninMailer {

    private static final Logger log = LoggerFactory.getLogger(SigninMailer.class);

    private final MailService mailService;
    private final String baseUrl;
    private final boolean logLinks;

    public SigninMailer(MailService mailService,
                        @Value("${app.base-url}") String baseUrl,
                        @Value("${app.signin.log-links:false}") boolean logLinks) {
        this.mailService = mailService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.logLinks = logLinks;
    }

    public void deliver(String email, String token, long validityMinutes) {
        String link = baseUrl + "/signin/" + token;
        if (mailService.isConfigured()) {
            mailService.send(email, "Your politigif sign-in link", body(link, validityMinutes));
        } else if (logLinks) {
            log.warn("mail not configured, sign-in link for {}: {}", email, link);
        } else {
            log.error("mail not configured and app.signin.log-links is off: {} cannot sign in", email);
        }
    }

    static String body(String link, long validityMinutes) {
        return "Tap to sign in to politigif:\n\n" + link + "\n\n"
                + "The link works once, for " + validityMinutes + " minutes, and only in the browser "
                + "where you asked for it.\n\nIf you did not ask for this, ignore it: nobody can use the "
                + "link without that browser.\n";
    }
}
