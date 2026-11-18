/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.status;

/**
 * {@code signin_links.status}. Only ACTIVE (and not past {@code expiresAt}) can be redeemed. There
 * is no TTL index: expired links are flipped to EXPIRED by {@code SigninLinkExpirer}, never removed.
 */
public enum SigninLinkStatus implements Status {
    DELETED(Status.DELETED),
    ACTIVE(Status.ACTIVE),
    USED(2),
    EXPIRED(3),
    /** Its account was reset before the link was used. */
    RETIRED_BY_RESET(4);

    private final int code;

    SigninLinkStatus(int code) {
        this.code = code;
    }

    @Override
    public int code() {
        return code;
    }
}
