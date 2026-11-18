/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.media;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-built GIFs for the pipe tests, plus an independent reader that sums the played duration of
 * whatever comes out (the oracle: it shares no code with {@link GifTrimPipe}).
 */
final class GifFixtures {

    /** A 1x1 image: min code size 2, one data sub-block, terminator (the classic smallest GIF body). */
    private static final byte[] IMAGE = {0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0x02, 0x02, 0x44, 0x01, 0x00};

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private GifFixtures(String version, boolean globalTable) {
        write(version.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        // Logical screen 1x1; packed: global table of 2 colors when asked.
        write(new byte[]{1, 0, 1, 0, (byte) (globalTable ? 0x80 : 0x00), 0, 0});
        if (globalTable) {
            write(new byte[]{0, 0, 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        }
    }

    static GifFixtures gif89a() {
        return new GifFixtures("GIF89a", true);
    }

    static GifFixtures gif87aNoGlobalTable() {
        return new GifFixtures("GIF87a", false);
    }

    /** A frame with a GCE carrying {@code delayCs}. */
    GifFixtures frame(int delayCs) {
        write(new byte[]{0x21, (byte) 0xF9, 4, 0, (byte) delayCs, (byte) (delayCs >>> 8), 0, 0});
        write(IMAGE);
        return this;
    }

    GifFixtures frames(int count, int delayCs) {
        for (int i = 0; i < count; i++) {
            frame(delayCs);
        }
        return this;
    }

    /** A frame with no GCE at all (plays as 10 cs). */
    GifFixtures frameWithoutGce() {
        write(IMAGE);
        return this;
    }

    /** A frame whose image carries a local color table of 2 colors. */
    GifFixtures frameWithLocalTable(int delayCs) {
        write(new byte[]{0x21, (byte) 0xF9, 4, 0, (byte) delayCs, (byte) (delayCs >>> 8), 0, 0});
        write(new byte[]{0x2C, 0, 0, 0, 0, 1, 0, 1, 0, (byte) 0x80});
        write(new byte[]{0, 0, 0, (byte) 0xFF, 0, 0});
        write(new byte[]{0x02, 0x02, 0x44, 0x01, 0x00});
        return this;
    }

    GifFixtures netscapeLoop() {
        write(new byte[]{0x21, (byte) 0xFF, 0x0B});
        write("NETSCAPE2.0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        write(new byte[]{3, 1, 0, 0, 0});
        return this;
    }

    GifFixtures comment(String text) {
        byte[] t = text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        write(new byte[]{0x21, (byte) 0xFE, (byte) t.length});
        write(t);
        write(new byte[]{0});
        return this;
    }

    GifFixtures raw(int... bytes) {
        for (int b : bytes) {
            out.write(b);
        }
        return this;
    }

    byte[] withTrailer() {
        out.write(0x3B);
        return out.toByteArray();
    }

    byte[] withoutTrailer() {
        return out.toByteArray();
    }

    private void write(byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    /** What a browser would play: each image's delay from the latest GCE before it (<2 → 10). */
    record Played(List<Integer> delays, boolean hasTrailer) {
        int total() {
            return delays.stream().mapToInt(Integer::intValue).sum();
        }
    }

    static Played play(byte[] gif) {
        int i = 6;
        int packed = gif[i + 4] & 0xFF;
        i += 7;
        if ((packed & 0x80) != 0) {
            i += 3 << ((packed & 7) + 1);
        }
        List<Integer> delays = new ArrayList<>();
        int pending = -1;
        while (i < gif.length) {
            int b = gif[i++] & 0xFF;
            if (b == 0x3B) {
                return new Played(delays, true);
            }
            if (b == 0x21) {
                int label = gif[i++] & 0xFF;
                if (label == 0xF9) {
                    pending = (gif[i + 2] & 0xFF) | (gif[i + 3] & 0xFF) << 8;
                }
                i = skipSubBlocks(gif, i);
            } else if (b == 0x2C) {
                int imgPacked = gif[i + 8] & 0xFF;
                i += 9;
                if ((imgPacked & 0x80) != 0) {
                    i += 3 << ((imgPacked & 7) + 1);
                }
                i++; // LZW min code size
                i = skipSubBlocks(gif, i);
                int d = pending < 0 ? 0 : pending;
                delays.add(d < 2 ? 10 : d);
                pending = -1;
            } else {
                throw new IllegalArgumentException("bad block at " + (i - 1));
            }
        }
        return new Played(delays, false);
    }

    private static int skipSubBlocks(byte[] gif, int i) {
        while (true) {
            int n = gif[i++] & 0xFF;
            if (n == 0) {
                return i;
            }
            i += n;
        }
    }
}
