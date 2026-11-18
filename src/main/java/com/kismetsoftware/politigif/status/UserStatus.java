/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.status;

/** {@code users.status}. Only one ACTIVE account per email. See {@link Status}. */
public enum UserStatus implements Status {
    DELETED(Status.DELETED),
    ACTIVE(Status.ACTIVE),
    /** Retired by an account reset; a fresh ACTIVE account takes the same email. */
    RESET(2);

    private final int code;

    UserStatus(int code) {
        this.code = code;
    }

    @Override
    public int code() {
        return code;
    }
}
