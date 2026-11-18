/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.status;

/** {@code reports.status}. ACTIVE = open, in the admin queue. See {@link Status}. */
public enum ReportStatus implements Status {
    DELETED(Status.DELETED),
    ACTIVE(Status.ACTIVE),
    /** An admin dismissed it; the GIF stays up. */
    DISMISSED(2),
    /** Closed because an admin hid the GIF. */
    CLOSED_BY_HIDE(3);

    private final int code;

    ReportStatus(int code) {
        this.code = code;
    }

    @Override
    public int code() {
        return code;
    }
}
