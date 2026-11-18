/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.media;

import com.kismetsoftware.politigif.storage.MediaStorage;
import dev.flamelens.fusio.ByteSource;
import dev.flamelens.fusio.Bytes;
import dev.flamelens.fusio.Pipe;
import dev.flamelens.fusio.Sink;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Every media byte stream in the app, each one a fusio pipeline.
 *
 * <p><b>In:</b> request body → {@link MagicBytesPipe} → {@link ByteCapPipe} (20 MB) →
 * {@link GifTrimPipe} (7.77 s) → ffmpeg stdin; ffmpeg stdout (H.264 MP4, yuv420p, even
 * dimensions) → an in-memory sink → storage. Only the MP4 is stored.
 *
 * <p><b>Out:</b> storage → fusio → ffmpeg stdin; ffmpeg stdout → fusio → the HTTP response.
 * Converted fresh on every request; nothing is cached, nothing touches disk.
 */
@Service
public class MediaService {

    /** The stored MP4 of a 7.77 s GIF is small; this only stops a pathological encode. */
    static final long MAX_MP4_BYTES = 64L * 1024 * 1024;
    private static final int CHUNK = 64 * 1024;

    /** The output formats converted on request. */
    public enum Format {
        // GIF timebase on the encoder, or the trimmed last frame is rounded up (7.77 s became 7.80 s).
        GIF("image/gif", List.of(
                "-fps_mode", "passthrough", "-enc_time_base", "1:100",
                "-vf", "split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer",
                "-loop", "0", "-f", "gif")),
        // libwebp_anim, not libwebp: through a pipe the webp muxer cannot seek back to fill in the
        // RIFF size (it stays 0, an invalid file), while libwebp_anim emits the whole file at once.
        // Known limit (ffmpeg 9.0.2, measured 2026-11-17): libwebp_anim gives the LAST frame the
        // previous frame's duration, so a trimmed 7.77 s GIF plays as up to 7.80 s in WebP.
        WEBP("image/webp", List.of(
                "-c:v", "libwebp_anim", "-quality", "75", "-loop", "0", "-an", "-f", "webp")),
        POSTER("image/jpeg", List.of(
                "-frames:v", "1", "-c:v", "mjpeg", "-q:v", "3", "-f", "image2pipe"));

        private final String contentType;
        private final List<String> outputArgs;

        Format(String contentType, List<String> outputArgs) {
            this.contentType = contentType;
            this.outputArgs = outputArgs;
        }

        public String contentType() {
            return contentType;
        }
    }

    /** A converted upload: the MP4 bytes to store and what the GIF pipe saw. */
    public record Converted(byte[] mp4, GifTrimPipe.Result gif) {
    }

    private final Ffmpeg ffmpeg;
    private final MediaStorage storage;
    private final long maxUploadBytes;

    public MediaService(Ffmpeg ffmpeg, MediaStorage storage,
                        @Value("${app.media.max-upload-bytes:20971520}") long maxUploadBytes) {
        this.ffmpeg = ffmpeg;
        this.storage = storage;
        this.maxUploadBytes = maxUploadBytes;
    }

    public long maxUploadBytes() {
        return maxUploadBytes;
    }

    /** The upload pipe for one request body. Throws {@link UploadRejectedException} on refused content. */
    public Converted convertUpload(InputStream body) throws IOException {
        GifTrimPipe trim = new GifTrimPipe();
        Pipe<Bytes, Bytes> gate = new MagicBytesPipe()
                .then(new ByteCapPipe(maxUploadBytes, "That GIF is over 20 MB. Try a smaller one."))
                .then(trim);
        ByteArrayOutputStream mp4 = new ByteArrayOutputStream();
        ffmpeg.run(uploadArgs(),
                stdin -> drain(ByteSource.from(body, CHUNK), gate, stdin),
                new ByteCapPipe(MAX_MP4_BYTES, "That GIF is too big to convert.")
                        .connect(new Ffmpeg.OutputStreamSink(mp4)));
        byte[] bytes = mp4.toByteArray();
        if (bytes.length == 0) {
            throw new UploadRejectedException(GifTrimPipe.BROKEN);
        }
        return new Converted(bytes, trim.result());
    }

    /** Converts the stored MP4 at {@code key} to {@code format}, streaming into {@code response}. */
    public void render(String key, Format format, OutputStream response) throws IOException {
        try (InputStream stored = storage.open(key)) {
            ffmpeg.run(renderArgs(format),
                    stdin -> drain(ByteSource.from(stored, CHUNK), Pipe.identity(), stdin),
                    new Ffmpeg.OutputStreamSink(response));
        }
    }

    /** The stored MP4 (or one byte range of it), passed straight through to the response. */
    public void passthrough(InputStream stored, OutputStream response) throws IOException {
        drain(ByteSource.from(stored, CHUNK), Pipe.identity(), new Ffmpeg.OutputStreamSink(response));
    }

    static List<String> uploadArgs() {
        return List.of("-hide_banner", "-loglevel", "error",
                "-f", "gif", "-i", "pipe:0",
                "-an",
                // H.264 in yuv420p needs even dimensions; round down by at most one pixel.
                "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
                // Keep the GIF's own timing: every frame's delay as-is, in GIF centiseconds. With
                // ffmpeg's default timebase the trimmed last frame (e.g. 7 cs) is rounded up to a
                // whole 10 fps tick and a 7.77 s GIF becomes a 7.80 s MP4 (measured 2026-11-17).
                "-fps_mode", "passthrough", "-enc_time_base", "1:100", "-video_track_timescale", "100",
                // stdout is not seekable: a fragmented MP4 puts the moov first, plays in every
                // browser, and is readable back from a pipe by the out conversions.
                "-movflags", "frag_keyframe+empty_moov+default_base_moof",
                "-f", "mp4", "pipe:1");
    }

    static List<String> renderArgs(Format format) {
        List<String> args = new java.util.ArrayList<>(List.of("-hide_banner", "-loglevel", "error",
                "-f", "mp4", "-i", "pipe:0"));
        args.addAll(format.outputArgs);
        args.add("pipe:1");
        return args;
    }

    /** Runs {@code source} through {@code pipe} into {@code sink}: fusio's push model, driven directly. */
    static void drain(ByteSource source, Pipe<Bytes, Bytes> pipe, Sink<Bytes> sink) throws IOException {
        Sink<Bytes> head = pipe.connect(sink);
        source.drainTo(head);
        head.end();
    }
}
