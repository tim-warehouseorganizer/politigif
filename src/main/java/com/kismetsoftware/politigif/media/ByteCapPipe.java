/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.media;

import dev.flamelens.fusio.Bytes;
import dev.flamelens.fusio.Pipe;
import dev.flamelens.fusio.Sink;

import java.io.IOException;

/**
 * Counts the bytes that pass and refuses the stream the moment it goes over {@code maxBytes}. The
 * chunk that crosses the line is not forwarded. Reusable: each {@link #connect} counts from zero.
 */
public final class ByteCapPipe implements Pipe<Bytes, Bytes> {

    private final long maxBytes;
    private final String message;

    public ByteCapPipe(long maxBytes, String message) {
        this.maxBytes = maxBytes;
        this.message = message;
    }

    @Override
    public Sink<Bytes> connect(Sink<Bytes> downstream) {
        return new Sink<>() {
            private long seen;

            @Override
            public boolean accept(Bytes chunk) throws IOException {
                seen += chunk.length;
                if (seen > maxBytes) {
                    throw new UploadRejectedException(message);
                }
                return downstream.accept(chunk);
            }

            @Override
            public void end() throws IOException {
                downstream.end();
            }
        };
    }
}
