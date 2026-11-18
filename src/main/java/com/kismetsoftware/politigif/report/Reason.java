/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.report;

/** Why a GIF was reported; the label is what the report form shows. */
public enum Reason {
    EXPLICIT("Sexual or graphic content"),
    HATE("Hate or harassment"),
    THREAT("Violence or threats"),
    PERSONAL_INFO("Someone's private information"),
    COPYRIGHT("It's my work, posted without permission"),
    SPAM("Spam"),
    OTHER("Something else");

    private final String label;

    Reason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
