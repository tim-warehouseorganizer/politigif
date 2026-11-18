/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.user;

import java.util.Locale;
import java.util.regex.Pattern;

/** One spelling of an email address everywhere it is stored, looked up or rate-limited. */
public final class Emails {

    /** Deliberately loose: the emailed link is the real validation. */
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$");

    private Emails() {
    }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean looksValid(String email) {
        String normalized = normalize(email);
        return normalized.length() <= 254 && SHAPE.matcher(normalized).matches();
    }
}
