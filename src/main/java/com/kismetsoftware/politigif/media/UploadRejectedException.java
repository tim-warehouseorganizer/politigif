/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.media;

/**
 * An upload refused on its content: not a GIF, too big, or broken. The message is written for the
 * person who uploaded it and is shown as-is.
 */
public class UploadRejectedException extends RuntimeException {

    public UploadRejectedException(String message) {
        super(message);
    }
}
