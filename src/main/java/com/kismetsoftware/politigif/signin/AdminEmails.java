/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.signin;

import com.kismetsoftware.politigif.user.Emails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Admins are named by configuration ({@code app.admin.emails}), read at each sign-in. */
@Component
public class AdminEmails {

    private final Set<String> emails;

    public AdminEmails(@Value("${app.admin.emails:}") String configured) {
        this.emails = Arrays.stream(configured.split(","))
                .map(Emails::normalize)
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String email) {
        return emails.contains(Emails.normalize(email));
    }
}
