/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.security;

import java.io.Serial;
import java.io.Serializable;

/**
 * Who is signed in. Kept in the Redis session (Kryo5), so it holds only ids, never secrets.
 * A plain class rather than a record so the session codec needs no special handling.
 */
public final class PolitigifPrincipal implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String userId;
    private String email;
    private boolean admin;

    /** For the session codec. */
    PolitigifPrincipal() {
    }

    public PolitigifPrincipal(String userId, String email, boolean admin) {
        this.userId = userId;
        this.email = email;
        this.admin = admin;
    }

    public String userId() {
        return userId;
    }

    public String email() {
        return email;
    }

    public boolean admin() {
        return admin;
    }

    @Override
    public String toString() {
        return userId;
    }
}
