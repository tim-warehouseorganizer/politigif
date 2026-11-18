/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where the MP4s live. Write-once and never deleted (the no-delete rule, see status.Status): there
 * is deliberately no delete method. Court-ordered removal is done by hand, see
 * docs/court-ordered-deletion.md.
 *
 * Production: DigitalOcean Spaces (S3-compatible). Dev: a local folder.
 * Keys are opaque strings chosen by the caller ({@code gifs/<id>.mp4}).
 */
public interface MediaStorage {

    void put(String key, byte[] data, String contentType) throws IOException;

    /** The whole object. The caller closes the stream. */
    InputStream open(String key) throws IOException;

    /** Bytes {@code [first, last]} inclusive, for HTTP Range requests. The caller closes the stream. */
    InputStream openRange(String key, long first, long last) throws IOException;

    /** Object size in bytes. */
    long size(String key) throws IOException;
}
