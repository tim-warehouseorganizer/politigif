/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.status;

/** {@code gifs.status}. Only ACTIVE is public. See {@link Status} for the rule and the WHO origin. */
public enum GifStatus implements Status {
    DELETED(Status.DELETED),
    ACTIVE(Status.ACTIVE),
    /** The owner hid it; the owner can unhide it. */
    HIDDEN_BY_OWNER(2),
    /** An admin hid it; the owner cannot undo this. The prior status is kept to restore on admin unhide. */
    HIDDEN_BY_ADMIN(3),
    /** Its account was reset: hidden and detached from the account (the original owner id is kept). */
    RETIRED_BY_RESET(4);

    private final int code;

    GifStatus(int code) {
        this.code = code;
    }

    @Override
    public int code() {
        return code;
    }

    public static GifStatus of(int code) {
        return Status.of(GifStatus.class, code);
    }
}
