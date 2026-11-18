/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.media;

import dev.flamelens.fusio.Bytes;
import dev.flamelens.fusio.Pipe;
import dev.flamelens.fusio.Sink;

import java.io.IOException;

/**
 * A streaming GIF87a/GIF89a pass-through that caps playback at exactly {@link #CAP_CS}
 * centiseconds (7.77 s) as a fusio {@link Pipe}.
 *
 * <p>A state machine walks the block structure as bytes arrive and sums each frame's Graphic
 * Control Extension delay. A delay below 2 cs counts as 10 cs, because that is what Chrome,
 * Firefox, Safari and ffmpeg's GIF demuxer all play; a frame with no GCE counts the same way.
 *
 * <p><b>Trimming.</b> When a frame would carry the total past 7.77 s, that frame's delay is
 * shortened so the total lands exactly on 7.77 s, the frame is passed through, the GIF trailer
 * ({@code 0x3B}) is emitted, and {@code accept()} returns {@code false} so the source stops
 * reading. Two edge cases keep it exact:
 * <ul>
 *   <li>the crossing frame has no GCE: a GCE carrying the shortened delay is inserted before it;</li>
 *   <li>only 1 cs remains: 1 cs plays as 10 cs, so 1 cs is borrowed from the previous frame
 *       (which must be at least 3 cs, so it stays at 2 or more) and the crossing frame gets 2 cs.
 *       If the previous frame cannot spare it, the GIF ends before the crossing frame, at 7.76 s:
 *       under the cap, never over it.</li>
 * </ul>
 * To make those changes possible after the fact, the pipe holds back the bytes of the frame
 * before the one being read (one frame of memory, bounded by the upload cap) and forwards
 * everything earlier as soon as it is final.
 *
 * <p>A GIF that ends exactly on 7.77 s and has nothing after is not "trimmed". A GIF missing its
 * trailer after a complete frame gets one appended (browsers accept those too); a GIF that ends
 * mid-frame, or holds no frame at all, is rejected as broken.
 *
 * <p>Single use: one instance per stream. Read {@link #result()} after {@code end()}.
 */
public final class GifTrimPipe implements Pipe<Bytes, Bytes> {

    /** 7.77 seconds, in GIF centiseconds. */
    public static final int CAP_CS = 777;
    /** Delays below this play as {@link #DEFAULT_DELAY_CS}. */
    static final int MIN_PLAYED_DELAY_CS = 2;
    static final int DEFAULT_DELAY_CS = 10;
    static final byte TRAILER = 0x3B;

    static final String BROKEN = "That GIF looks broken, so it can't be converted.";

    /** What the pipe saw and did. */
    public record Result(int width, int height, int frames, int durationCs, boolean trimmed) {
    }

    private enum State {
        HEADER, SCREEN, GLOBAL_TABLE, BLOCK_INTRO, EXT_LABEL, GCE_SIZE, GCE_BODY, SUB_LEN, SUB_DATA,
        IMAGE_DESC, LOCAL_TABLE, LZW_MIN, IMAGE_LEN, IMAGE_DATA, PEEK_AFTER_CAP, DONE
    }

    /** What happens when the current image's data ends. */
    private enum AfterFrame { CONTINUE, LANDED_EXACTLY, STOP }

    private State state = State.HEADER;
    private final byte[] field = new byte[9];
    private int fieldHave;
    private int skip;

    /** Bytes not yet forwarded: the previous frame (from its delay slot) onward. */
    private byte[] out = new byte[64 * 1024];
    private int outLen;

    // The most recent GCE since the last image: where it starts, where its delay sits, the delay.
    private int gceStart = -1;
    private int gceDelayPos = -1;
    private int gceDelay;
    private int extStart;

    // The previous frame's delay slot, for the 1 cs borrow.
    private boolean hasPrev;
    private int prevSlotStart;
    private int prevDelayPos = -1;
    private int prevEffective;

    private AfterFrame afterFrame = AfterFrame.CONTINUE;
    private int totalCs;
    private int frames;
    private int width;
    private int height;
    private boolean trimmed;
    private boolean connected;
    private boolean ended;

    @Override
    public Sink<Bytes> connect(Sink<Bytes> downstream) {
        if (connected) {
            throw new IllegalStateException("GifTrimPipe is single-use");
        }
        connected = true;
        return new Sink<>() {
            @Override
            public boolean accept(Bytes chunk) throws IOException {
                if (state == State.DONE) {
                    return false;
                }
                return consume(chunk.array, chunk.offset, chunk.offset + chunk.length, downstream);
            }

            @Override
            public void end() throws IOException {
                finish(downstream);
            }
        };
    }

    /** Final once {@code end()} has run. */
    public Result result() {
        if (!ended) {
            throw new IllegalStateException("result() before end()");
        }
        return new Result(width, height, frames, totalCs, trimmed);
    }

    static int playedDelay(int delayCs) {
        return delayCs < MIN_PLAYED_DELAY_CS ? DEFAULT_DELAY_CS : delayCs;
    }

    private boolean consume(byte[] a, int i, int end, Sink<Bytes> down) throws IOException {
        while (i < end) {
            switch (state) {
                case SUB_DATA, IMAGE_DATA, GLOBAL_TABLE, LOCAL_TABLE -> {
                    int n = Math.min(skip, end - i);
                    append(a, i, n);
                    i += n;
                    skip -= n;
                    if (skip == 0) {
                        state = switch (state) {
                            case SUB_DATA -> State.SUB_LEN;
                            case IMAGE_DATA -> State.IMAGE_LEN;
                            case GLOBAL_TABLE -> State.BLOCK_INTRO;
                            default -> State.LZW_MIN;
                        };
                    }
                }
                default -> {
                    if (!step(a[i++], down)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** One byte of a structural field. Returns false once the GIF is finished. */
    private boolean step(byte b, Sink<Bytes> down) throws IOException {
        int u = b & 0xFF;
        switch (state) {
            case HEADER -> {
                append(b);
                field[fieldHave++] = b;
                if (fieldHave == 6) {
                    if (!MagicBytesPipe.isGifHeader(field)) {
                        throw new UploadRejectedException(MagicBytesPipe.NOT_A_GIF);
                    }
                    fieldHave = 0;
                    state = State.SCREEN;
                }
            }
            case SCREEN -> {
                append(b);
                field[fieldHave++] = b;
                if (fieldHave == 7) {
                    fieldHave = 0;
                    width = le16(field, 0);
                    height = le16(field, 2);
                    if ((field[4] & 0x80) != 0) {
                        skip = 3 << ((field[4] & 0x07) + 1);
                        state = State.GLOBAL_TABLE;
                    } else {
                        state = State.BLOCK_INTRO;
                    }
                }
            }
            case BLOCK_INTRO -> {
                if (u == 0x21) {
                    extStart = outLen;
                    append(b);
                    state = State.EXT_LABEL;
                } else if (u == 0x2C) {
                    return beginImage(b, down);
                } else if (u == 0x3B) {
                    append(b);
                    return complete(down);
                } else {
                    throw new UploadRejectedException(BROKEN);
                }
            }
            case EXT_LABEL -> {
                append(b);
                state = u == 0xF9 ? State.GCE_SIZE : State.SUB_LEN;
            }
            case GCE_SIZE -> {
                if (u != 4) {
                    throw new UploadRejectedException(BROKEN);
                }
                append(b);
                state = State.GCE_BODY;
            }
            case GCE_BODY -> {
                if (fieldHave == 1) {
                    gceDelayPos = outLen;
                }
                append(b);
                field[fieldHave++] = b;
                if (fieldHave == 4) {
                    fieldHave = 0;
                    gceStart = extStart;
                    gceDelay = le16(field, 1);
                    // The block terminator is read as an empty sub-block.
                    state = State.SUB_LEN;
                }
            }
            case SUB_LEN -> {
                append(b);
                if (u == 0) {
                    state = State.BLOCK_INTRO;
                } else {
                    skip = u;
                    state = State.SUB_DATA;
                }
            }
            case IMAGE_DESC -> {
                append(b);
                field[fieldHave++] = b;
                if (fieldHave == 9) {
                    fieldHave = 0;
                    if ((field[8] & 0x80) != 0) {
                        skip = 3 << ((field[8] & 0x07) + 1);
                        state = State.LOCAL_TABLE;
                    } else {
                        state = State.LZW_MIN;
                    }
                }
            }
            case LZW_MIN -> {
                append(b);
                state = State.IMAGE_LEN;
            }
            case IMAGE_LEN -> {
                append(b);
                if (u != 0) {
                    skip = u;
                    state = State.IMAGE_DATA;
                } else {
                    return endImage(down);
                }
            }
            case PEEK_AFTER_CAP -> {
                // The last frame landed exactly on the cap. A trailer next means nothing was cut.
                if (u != 0x3B) {
                    trimmed = true;
                }
                append(TRAILER);
                return complete(down);
            }
            default -> throw new IllegalStateException("unexpected state " + state);
        }
        return true;
    }

    /** At an image separator: the frame's delay is final, so decide whether it crosses the cap. */
    private boolean beginImage(byte separator, Sink<Bytes> down) throws IOException {
        boolean hasGce = gceDelayPos >= 0;
        int slotStart = hasGce ? gceStart : outLen;
        int effective = playedDelay(hasGce ? gceDelay : 0);
        int delayPos = hasGce ? gceDelayPos : -1;

        if (totalCs + effective < CAP_CS) {
            totalCs += effective;
        } else if (totalCs + effective == CAP_CS) {
            totalCs = CAP_CS;
            afterFrame = AfterFrame.LANDED_EXACTLY;
        } else {
            int remaining = CAP_CS - totalCs;
            if (remaining >= MIN_PLAYED_DELAY_CS) {
                delayPos = setDelay(slotStart, delayPos, remaining);
            } else if (hasPrev && prevEffective - 1 >= MIN_PLAYED_DELAY_CS) {
                int shift = setPrevDelay(prevEffective - 1);
                slotStart += shift;
                if (delayPos >= 0) {
                    delayPos += shift;
                }
                delayPos = setDelay(slotStart, delayPos, MIN_PLAYED_DELAY_CS);
            } else {
                // Cannot land exactly: end before this frame, under the cap rather than over it.
                outLen = slotStart;
                trimmed = true;
                append(TRAILER);
                return complete(down);
            }
            totalCs = CAP_CS;
            trimmed = true;
            afterFrame = AfterFrame.STOP;
        }

        // Everything before this frame's slot is final now: forward it, keep this frame back.
        if (!flush(slotStart, down)) {
            state = State.DONE;
            return false;
        }
        hasPrev = true;
        prevSlotStart = 0;
        prevDelayPos = delayPos < 0 ? -1 : delayPos - slotStart;
        prevEffective = effective;
        gceStart = -1;
        gceDelayPos = -1;

        append(separator);
        state = State.IMAGE_DESC;
        return true;
    }

    private boolean endImage(Sink<Bytes> down) throws IOException {
        frames++;
        switch (afterFrame) {
            case STOP -> {
                append(TRAILER);
                return complete(down);
            }
            case LANDED_EXACTLY -> state = State.PEEK_AFTER_CAP;
            default -> state = State.BLOCK_INTRO;
        }
        return true;
    }

    /**
     * Writes {@code delayCs} into the frame's GCE, or inserts a GCE carrying it at
     * {@code slotStart} when the frame has none. Returns the delay's position.
     */
    private int setDelay(int slotStart, int delayPos, int delayCs) {
        if (delayPos >= 0) {
            out[delayPos] = (byte) delayCs;
            out[delayPos + 1] = (byte) (delayCs >>> 8);
            return delayPos;
        }
        byte[] gce = {0x21, (byte) 0xF9, 0x04, 0x00, (byte) delayCs, (byte) (delayCs >>> 8), 0x00, 0x00};
        insert(slotStart, gce);
        return slotStart + 4;
    }

    /** Shortens the held-back previous frame; returns how far later bytes moved (8 if a GCE was inserted). */
    private int setPrevDelay(int delayCs) {
        if (prevDelayPos >= 0) {
            setDelay(prevSlotStart, prevDelayPos, delayCs);
            return 0;
        }
        setDelay(prevSlotStart, -1, delayCs);
        return 8;
    }

    private boolean complete(Sink<Bytes> down) throws IOException {
        state = State.DONE;
        flush(outLen, down);
        return false;
    }

    private void finish(Sink<Bytes> down) throws IOException {
        if (ended) {
            return;
        }
        ended = true;
        if (state == State.PEEK_AFTER_CAP || (state == State.BLOCK_INTRO && frames > 0)) {
            // Stream ended after a whole frame with no trailer: supply it.
            append(TRAILER);
            state = State.DONE;
            flush(outLen, down);
        } else if (state != State.DONE) {
            throw new UploadRejectedException(BROKEN);
        }
        if (frames == 0) {
            throw new UploadRejectedException(BROKEN);
        }
        down.end();
    }

    /** Forwards {@code [0, upTo)}; false when the downstream wants no more. */
    private boolean flush(int upTo, Sink<Bytes> down) throws IOException {
        if (upTo == 0) {
            return true;
        }
        // The downstream sees a view valid only during accept (fusio's ownership contract).
        boolean more = down.accept(new Bytes(out, 0, upTo));
        System.arraycopy(out, upTo, out, 0, outLen - upTo);
        outLen -= upTo;
        if (gceStart >= 0) {
            gceStart -= upTo;
        }
        if (gceDelayPos >= 0) {
            gceDelayPos -= upTo;
        }
        extStart -= upTo;
        return more;
    }

    private void append(byte b) {
        ensure(1);
        out[outLen++] = b;
    }

    private void append(byte[] a, int off, int len) {
        ensure(len);
        System.arraycopy(a, off, out, outLen, len);
        outLen += len;
    }

    private void insert(int pos, byte[] bytes) {
        ensure(bytes.length);
        System.arraycopy(out, pos, out, pos + bytes.length, outLen - pos);
        System.arraycopy(bytes, 0, out, pos, bytes.length);
        outLen += bytes.length;
    }

    private void ensure(int extra) {
        if (outLen + extra > out.length) {
            out = java.util.Arrays.copyOf(out, Math.max(out.length * 2, outLen + extra));
        }
    }

    private static int le16(byte[] a, int off) {
        return (a[off] & 0xFF) | (a[off + 1] & 0xFF) << 8;
    }
}
