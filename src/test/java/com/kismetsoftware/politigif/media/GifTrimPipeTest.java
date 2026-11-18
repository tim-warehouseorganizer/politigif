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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GifTrimPipeTest {

    /** Output bytes, the pipe's result, and how many input bytes were read before it stopped. */
    record Run(byte[] out, GifTrimPipe.Result result, long bytesRead) {
    }

    private static Run run(byte[] input, int chunk) throws IOException {
        GifTrimPipe trim = new GifTrimPipe();
        long[] read = {0};
        Pipe<Bytes, Bytes> counter = down -> new Sink<>() {
            @Override
            public boolean accept(Bytes b) throws IOException {
                read[0] += b.length;
                return down.accept(b);
            }

            @Override
            public void end() throws IOException {
                down.end();
            }
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Sink<Bytes> head = counter.then(trim).connect(new Sink<>() {
            @Override
            public boolean accept(Bytes b) {
                out.write(b.array, b.offset, b.length);
                return true;
            }
        });
        ByteSource.of(input, chunk).drainTo(head);
        head.end();
        return new Run(out.toByteArray(), trim.result(), read[0]);
    }

    private static Run run(byte[] input) throws IOException {
        return run(input, 64 * 1024);
    }

    // ---- under the cap: untouched ----

    @Test
    void aShortGifPassesThroughByteForByte() throws IOException {
        byte[] gif = GifFixtures.gif89a().frames(3, 10).withTrailer();

        Run run = run(gif);

        assertThat(run.out()).isEqualTo(gif);
        assertThat(run.result()).isEqualTo(new GifTrimPipe.Result(1, 1, 3, 30, false));
    }

    @Test
    void delaysOfZeroAndOneCountAsTenLikeBrowsers() throws IOException {
        byte[] gif = GifFixtures.gif89a().frame(0).frame(1).frame(2).withTrailer();

        Run run = run(gif);

        assertThat(run.result().durationCs()).isEqualTo(10 + 10 + 2);
        assertThat(run.out()).isEqualTo(gif);
    }

    @Test
    void aFrameWithoutAGceCountsAsTen() throws IOException {
        byte[] gif = GifFixtures.gif87aNoGlobalTable().frameWithoutGce().frameWithoutGce().withTrailer();

        Run run = run(gif);

        assertThat(run.result().durationCs()).isEqualTo(20);
        assertThat(run.result().frames()).isEqualTo(2);
        assertThat(run.out()).isEqualTo(gif);
    }

    @Test
    void extensionsAndLocalColorTablesPassThrough() throws IOException {
        byte[] gif = GifFixtures.gif89a().netscapeLoop().comment("hi").frameWithLocalTable(20).frame(30).withTrailer();

        Run run = run(gif);

        assertThat(run.out()).isEqualTo(gif);
        assertThat(run.result().durationCs()).isEqualTo(50);
    }

    // ---- over the cap: trimmed to exactly 7.77 s ----

    @Test
    void aLongGifIsTrimmedToExactlySevenSeventySeven() throws IOException {
        byte[] gif = GifFixtures.gif89a().frames(100, 10).withTrailer();

        Run run = run(gif);

        GifFixtures.Played played = GifFixtures.play(run.out());
        assertThat(played.total()).isEqualTo(777);
        assertThat(played.hasTrailer()).isTrue();
        assertThat(played.delays()).hasSize(78);
        assertThat(played.delays().getLast()).isEqualTo(7);
        assertThat(run.result()).isEqualTo(new GifTrimPipe.Result(1, 1, 78, 777, true));
    }

    @Test
    void readingStopsAtTheCrossingFrame() throws IOException {
        byte[] gif = GifFixtures.gif89a().frames(1000, 10).withTrailer();

        Run run = run(gif, 64);

        assertThat(run.bytesRead()).isLessThan(gif.length / 10);
        assertThat(run.out()[run.out().length - 1]).isEqualTo((byte) 0x3B);
    }

    @Test
    void theTrimIsTheSameWhateverTheChunkSize() throws IOException {
        byte[] gif = GifFixtures.gif89a().netscapeLoop().frames(50, 17).frameWithoutGce().frames(50, 13).withTrailer();

        Run whole = run(gif);
        Run byteByByte = run(gif, 1);
        Run odd = run(gif, 7);

        assertThat(byteByByte.out()).isEqualTo(whole.out());
        assertThat(odd.out()).isEqualTo(whole.out());
        assertThat(GifFixtures.play(whole.out()).total()).isEqualTo(777);
    }

    @Test
    void aCrossingFrameWithoutAGceGetsOneInserted() throws IOException {
        // 76 x 10 = 760, then a GCE-less frame (plays 10) would reach 770, then another to 780.
        byte[] gif = GifFixtures.gif89a().frames(76, 10).frameWithoutGce().frameWithoutGce().withTrailer();

        Run run = run(gif);

        GifFixtures.Played played = GifFixtures.play(run.out());
        assertThat(played.total()).isEqualTo(777);
        assertThat(played.delays().getLast()).isEqualTo(7);
        assertThat(run.result().trimmed()).isTrue();
    }

    @Test
    void landingExactlyOnTheCapWithNothingAfterIsNotTrimmed() throws IOException {
        byte[] gif = GifFixtures.gif89a().frames(70, 11).frame(7).withTrailer();

        Run run = run(gif);

        assertThat(run.out()).isEqualTo(gif);
        assertThat(run.result()).isEqualTo(new GifTrimPipe.Result(1, 1, 71, 777, false));
    }

    @Test
    void landingExactlyOnTheCapWithMoreFramesAfterIsTrimmedThere() throws IOException {
        byte[] gif = GifFixtures.gif89a().frames(70, 11).frame(7).frames(5, 10).withTrailer();

        Run run = run(gif);

        GifFixtures.Played played = GifFixtures.play(run.out());
        assertThat(played.total()).isEqualTo(777);
        assertThat(played.delays()).hasSize(71);
        assertThat(played.hasTrailer()).isTrue();
        assertThat(run.result().trimmed()).isTrue();
    }

    @Test
    void oneCentisecondLeftIsBorrowedFromThePreviousFrame() throws IOException {
        // 97 x 8 = 776; the next frame would leave 1 cs, which plays as 10 cs.
        byte[] gif = GifFixtures.gif89a().frames(97, 8).frame(10).withTrailer();

        Run run = run(gif);

        GifFixtures.Played played = GifFixtures.play(run.out());
        assertThat(played.total()).isEqualTo(777);
        List<Integer> d = played.delays();
        assertThat(d.subList(d.size() - 2, d.size())).containsExactly(7, 2);
        assertThat(run.result().trimmed()).isTrue();
    }

    @Test
    void oneCentisecondLeftBorrowsFromAPreviousFrameWithoutAGce() throws IOException {
        // 61 x 12 = 732, + 34 = 766, + a GCE-less frame (10) = 776: the borrow inserts a GCE of 9.
        byte[] gif = GifFixtures.gif89a().frames(61, 12).frame(34).frameWithoutGce().frame(10).withTrailer();

        Run run = run(gif);

        GifFixtures.Played played = GifFixtures.play(run.out());
        assertThat(played.total()).isEqualTo(777);
        List<Integer> d = played.delays();
        assertThat(d.subList(d.size() - 2, d.size())).containsExactly(9, 2);
    }

    @Test
    void whenThePreviousFrameCannotSpareACentisecondTheGifEndsBeforeTheCap() throws IOException {
        // 774 + 2 = 776 with a previous frame of 2 cs: borrowing would leave it at 1 (plays as 10).
        byte[] gif = GifFixtures.gif89a().frames(86, 9).frame(2).frame(10).withTrailer();

        Run run = run(gif);

        GifFixtures.Played played = GifFixtures.play(run.out());
        assertThat(played.total()).isEqualTo(776);
        assertThat(played.hasTrailer()).isTrue();
        assertThat(run.result()).isEqualTo(new GifTrimPipe.Result(1, 1, 87, 776, true));
    }

    // ---- endings and malformed input ----

    @Test
    void aMissingTrailerAfterAWholeFrameIsSupplied() throws IOException {
        byte[] gif = GifFixtures.gif89a().frames(2, 10).withoutTrailer();

        Run run = run(gif);

        assertThat(run.out()).isEqualTo(GifFixtures.gif89a().frames(2, 10).withTrailer());
        assertThat(run.result().trimmed()).isFalse();
    }

    @Test
    void aGifCutOffMidFrameIsRejected() {
        byte[] whole = GifFixtures.gif89a().frames(2, 10).withTrailer();
        byte[] cut = Arrays.copyOf(whole, whole.length - 5);

        assertThatThrownBy(() -> run(cut)).isInstanceOf(UploadRejectedException.class)
                .hasMessage(GifTrimPipe.BROKEN);
    }

    @Test
    void aGifWithNoFramesIsRejected() {
        byte[] empty = GifFixtures.gif89a().withTrailer();

        assertThatThrownBy(() -> run(empty)).isInstanceOf(UploadRejectedException.class);
    }

    @Test
    void anUnknownBlockIsRejected() {
        byte[] bad = GifFixtures.gif89a().frame(10).raw(0x99).withTrailer();

        assertThatThrownBy(() -> run(bad)).isInstanceOf(UploadRejectedException.class)
                .hasMessage(GifTrimPipe.BROKEN);
    }

    @Test
    void aGceOfTheWrongSizeIsRejected() {
        byte[] bad = GifFixtures.gif89a().raw(0x21, 0xF9, 5, 0, 0, 0, 0, 0, 0).withTrailer();

        assertThatThrownBy(() -> run(bad)).isInstanceOf(UploadRejectedException.class);
    }

    @Test
    void aNonGifHeaderIsRejected() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0};

        assertThatThrownBy(() -> run(png)).isInstanceOf(UploadRejectedException.class)
                .hasMessage(MagicBytesPipe.NOT_A_GIF);
    }

    @Test
    void aDownstreamThatWantsNoMoreStopsThePipe() throws IOException {
        byte[] gif = GifFixtures.gif89a().frames(10, 10).withTrailer();
        GifTrimPipe trim = new GifTrimPipe();
        Sink<Bytes> head = trim.connect(b -> false);

        boolean more = head.accept(new Bytes(gif, 0, gif.length));

        assertThat(more).isFalse();
    }

    @Test
    void resultBeforeEndIsRefused() {
        assertThatThrownBy(() -> new GifTrimPipe().result()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void thePipeIsSingleUse() {
        GifTrimPipe trim = new GifTrimPipe();
        trim.connect(b -> true);

        assertThatThrownBy(() -> trim.connect(b -> true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void playedDelayFollowsTheBrowserRule() {
        assertThat(GifTrimPipe.playedDelay(0)).isEqualTo(10);
        assertThat(GifTrimPipe.playedDelay(1)).isEqualTo(10);
        assertThat(GifTrimPipe.playedDelay(2)).isEqualTo(2);
    }
}
