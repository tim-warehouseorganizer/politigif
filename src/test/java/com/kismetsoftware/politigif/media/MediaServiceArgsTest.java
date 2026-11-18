/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.media;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The ffmpeg command lines are the contract with ffmpeg; pin the parts that were learned the hard way. */
class MediaServiceArgsTest {

    @Test
    void uploadsReadAGifFromStdinAndWriteH264Yuv420pMp4ToStdout() {
        List<String> args = MediaService.uploadArgs();

        assertThat(String.join(" ", args))
                .contains("-f gif -i pipe:0")
                .contains("-c:v libx264")
                .contains("-pix_fmt yuv420p")
                .contains("scale=trunc(iw/2)*2:trunc(ih/2)*2")
                .endsWith("-f mp4 pipe:1");
    }

    @Test
    void uploadsAreFragmentedBecauseStdoutCannotSeek() {
        assertThat(String.join(" ", MediaService.uploadArgs()))
                .contains("-movflags frag_keyframe+empty_moov+default_base_moof");
    }

    @Test
    void uploadsKeepGifTimingInCentiseconds() {
        // Without these a 7.77 s GIF became a 7.80 s MP4.
        assertThat(String.join(" ", MediaService.uploadArgs()))
                .contains("-fps_mode passthrough")
                .contains("-enc_time_base 1:100")
                .contains("-video_track_timescale 100");
    }

    @Test
    void theGifRenderKeepsCentisecondTiming() {
        assertThat(String.join(" ", MediaService.renderArgs(MediaService.Format.GIF)))
                .contains("-fps_mode passthrough -enc_time_base 1:100");
    }

    @Test
    void theWebpRenderUsesTheEncoderThatWritesAValidFileToAPipe() {
        assertThat(MediaService.renderArgs(MediaService.Format.WEBP)).contains("libwebp_anim");
    }

    @Test
    void rendersReadTheStoredMp4FromStdinAndWriteToStdout() {
        for (MediaService.Format format : MediaService.Format.values()) {
            List<String> args = MediaService.renderArgs(format);
            assertThat(String.join(" ", args)).as(format.name()).contains("-f mp4 -i pipe:0").endsWith("pipe:1");
        }
    }

    @Test
    void eachFormatHasItsContentType() {
        assertThat(MediaService.Format.GIF.contentType()).isEqualTo("image/gif");
        assertThat(MediaService.Format.WEBP.contentType()).isEqualTo("image/webp");
        assertThat(MediaService.Format.POSTER.contentType()).isEqualTo("image/jpeg");
        assertThat(String.join(" ", MediaService.renderArgs(MediaService.Format.POSTER))).contains("-frames:v 1");
        assertThat(String.join(" ", MediaService.renderArgs(MediaService.Format.WEBP))).contains("-loop 0");
    }
}
