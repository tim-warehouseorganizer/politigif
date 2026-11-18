/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.media;

import dev.flamelens.fusio.ByteSource;
import dev.flamelens.fusio.Bytes;
import dev.flamelens.fusio.Pipe;
import dev.flamelens.fusio.Sink;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MagicAndCapPipesTest {

    private static byte[] through(Pipe<Bytes, Bytes> pipe, byte[] input, int chunk) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Sink<Bytes> head = pipe.connect(b -> {
            out.write(b.array, b.offset, b.length);
            return true;
        });
        ByteSource.of(input, chunk).drainTo(head);
        head.end();
        return out.toByteArray();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    // ---- MagicBytesPipe ----

    @Test
    void gif89aPassesUntouched() throws IOException {
        byte[] in = bytes("GIF89a-rest-of-file");

        assertThat(through(new MagicBytesPipe(), in, 1024)).isEqualTo(in);
    }

    @Test
    void gif87aPassesUntouched() throws IOException {
        byte[] in = bytes("GIF87a-rest");

        assertThat(through(new MagicBytesPipe(), in, 1024)).isEqualTo(in);
    }

    @Test
    void aHeaderSplitAcrossChunksStillPasses() throws IOException {
        byte[] in = bytes("GIF89a-rest-of-file");

        assertThat(through(new MagicBytesPipe(), in, 1)).isEqualTo(in);
        assertThat(through(new MagicBytesPipe(), in, 4)).isEqualTo(in);
    }

    @Test
    void anythingElseIsRejected() {
        assertThatThrownBy(() -> through(new MagicBytesPipe(), bytes("\u0089PNG\r\n\u001a\n...."), 1024))
                .isInstanceOf(UploadRejectedException.class).hasMessage(MagicBytesPipe.NOT_A_GIF);
        assertThatThrownBy(() -> through(new MagicBytesPipe(), bytes("GIF88a......"), 1024))
                .isInstanceOf(UploadRejectedException.class);
    }

    @Test
    void aStreamShorterThanTheHeaderIsRejectedAtTheEnd() {
        assertThatThrownBy(() -> through(new MagicBytesPipe(), bytes("GIF8"), 1024))
                .isInstanceOf(UploadRejectedException.class);
        assertThatThrownBy(() -> through(new MagicBytesPipe(), new byte[0], 1024))
                .isInstanceOf(UploadRejectedException.class);
    }

    // ---- ByteCapPipe ----

    @Test
    void underTheCapEverythingPasses() throws IOException {
        byte[] in = new byte[99];

        assertThat(through(new ByteCapPipe(100, "big"), in, 10)).hasSize(99);
    }

    @Test
    void exactlyTheCapPasses() throws IOException {
        byte[] in = new byte[100];

        assertThat(through(new ByteCapPipe(100, "big"), in, 10)).hasSize(100);
    }

    @Test
    void overTheCapIsRefusedAndTheCrossingChunkIsNotForwarded() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Sink<Bytes> head = new ByteCapPipe(100, "too big").connect(b -> {
            out.write(b.array, b.offset, b.length);
            return true;
        });

        assertThatThrownBy(() -> ByteSource.of(new byte[101], 10).drainTo(head))
                .isInstanceOf(UploadRejectedException.class).hasMessage("too big");
        assertThat(out.size()).isEqualTo(100);
    }

    @Test
    void eachConnectCountsFromZero() throws IOException {
        ByteCapPipe cap = new ByteCapPipe(100, "big");

        through(cap, new byte[80], 10);

        assertThat(through(cap, new byte[80], 10)).hasSize(80);
    }
}
