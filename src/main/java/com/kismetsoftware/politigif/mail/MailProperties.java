/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMTP settings, ported from the flamelens/taxdocscanner donor.
 *
 * <p>{@link #username()} is separate from {@link #notificationEmail()} because the two are only the
 * same thing on a mailbox-based relay. A relay like Resend authenticates with an account credential
 * (username {@code resend}, password an API key) and lets you send from any verified domain, so the
 * login and the visible sender are independent. Leave {@code username} unset to authenticate as the
 * From address (Gmail-style).
 */
@ConfigurationProperties("app.mail")
public record MailProperties(
        String host,
        String port,
        String auth,
        String starttlsEnabled,
        /** SMTP auth user. Blank means "authenticate as {@link #notificationEmail()}". */
        String username,
        /** The visible From address. Must be on a domain verified with the mail provider. */
        String notificationEmail,
        String notificationPassword
) {

    /** The user to authenticate as: the explicit one, or the From address when none is configured. */
    public String resolvedUsername() {
        return username == null || username.isBlank() ? notificationEmail : username;
    }
}
