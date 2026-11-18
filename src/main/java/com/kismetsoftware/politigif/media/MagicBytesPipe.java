/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.media;

import dev.flamelens.fusio.Bytes;
import dev.flamelens.fusio.Pipe;
import dev.flamelens.fusio.Sink;

import java.io.IOException;

/**
 * The first gate of the upload pipeline: the stream must open with {@code GIF87a} or
 * {@code GIF89a}, whatever the file is called or claims to be. Holds back at most six bytes (the
 * header may arrive split across chunks), then passes everything through untouched.
 */
public final class MagicBytesPipe implements Pipe<Bytes, Bytes> {

    static final String NOT_A_GIF = "That isn't a GIF. Only GIF files can be uploaded.";

    @Override
    public Sink<Bytes> connect(Sink<Bytes> downstream) {
        return new Sink<>() {
            private final byte[] header = new byte[6];
            private int have;

            @Override
            public boolean accept(Bytes chunk) throws IOException {
                if (have == header.length) {
                    return downstream.accept(chunk);
                }
                int take = Math.min(header.length - have, chunk.length);
                System.arraycopy(chunk.array, chunk.offset, header, have, take);
                have += take;
                if (have < header.length) {
                    return true;
                }
                if (!isGifHeader(header)) {
                    throw new UploadRejectedException(NOT_A_GIF);
                }
                if (!downstream.accept(new Bytes(header, 0, header.length))) {
                    return false;
                }
                int rest = chunk.length - take;
                return rest == 0 || downstream.accept(new Bytes(chunk.array, chunk.offset + take, rest));
            }

            @Override
            public void end() throws IOException {
                if (have < header.length) {
                    throw new UploadRejectedException(NOT_A_GIF);
                }
                downstream.end();
            }
        };
    }

    static boolean isGifHeader(byte[] h) {
        return h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8'
                && (h[4] == '7' || h[4] == '9') && h[5] == 'a';
    }
}
