/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.config.logging;

/**
 * The MDC vocabulary, same convention as the sibling apps. Keys are deliberately terse — every log
 * line carries the whole MDC, so each character is paid for on every line.
 */
public final class MdcKeys {

    /** Trace id: follows one logical operation. */
    public static final String TRACE = "t";
    /** Request id: pins the HTTP request. */
    public static final String REQUEST = "r";
    /** The signed-in user's uuid, {@code ANONYMOUS}, or a synthetic actor for background jobs. */
    public static final String CURRENT_USER = "cu";
    public static final String URI = "uri";
    public static final String HTTP_METHOD = "hm";

    /** GIF id, parsed from {@code /g/<id>} paths by RequestTraceFilter. */
    public static final String GIF_ID = "gid";

    private MdcKeys() {
    }
}
