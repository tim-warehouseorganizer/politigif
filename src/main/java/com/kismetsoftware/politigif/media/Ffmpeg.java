/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.media;

import dev.flamelens.fusio.ByteSource;
import dev.flamelens.fusio.Bytes;
import dev.flamelens.fusio.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs ffmpeg as a stream filter: a feeder pushes bytes into its stdin while its stdout is drained
 * into a fusio {@link Sink} on the calling thread. No temp files: ffmpeg reads {@code pipe:0} and
 * writes {@code pipe:1}. The feeder runs on a virtual thread; stderr is drained on another so a
 * chatty ffmpeg can never block on a full pipe.
 *
 * <p>Error precedence: an {@link UploadRejectedException} from the feeder (the GIF itself was
 * refused) always wins; then a non-zero ffmpeg exit; an I/O error writing ffmpeg's stdin is
 * ignored when ffmpeg exited cleanly (a poster frame needs only the start of the input, so ffmpeg
 * closes its stdin early on purpose).
 */
@Component
public class Ffmpeg {

    private static final Logger log = LoggerFactory.getLogger(Ffmpeg.class);
    private static final int CHUNK = 64 * 1024;
    private static final long TIMEOUT_SECONDS = 120;

    /** Pushes the input into ffmpeg's stdin (as a fusio sink); must call {@code end()} when done. */
    @FunctionalInterface
    public interface Feeder {
        void feed(Sink<Bytes> stdin) throws IOException;
    }

    private final String executable;

    public Ffmpeg(@Value("${app.media.ffmpeg-path:ffmpeg}") String executable) {
        this.executable = executable;
    }

    /**
     * Runs ffmpeg with {@code args} (everything after the executable), feeding stdin from
     * {@code feeder} and draining stdout into {@code stdout}. Returns when ffmpeg has exited.
     */
    public void run(List<String> args, Feeder feeder, Sink<Bytes> stdout) throws IOException {
        List<String> command = new ArrayList<>(args.size() + 1);
        command.add(executable);
        command.addAll(args);
        Process process = new ProcessBuilder(command).start();

        StringBuilder stderr = new StringBuilder();
        Thread stderrDrain = Thread.ofVirtual().start(() -> drainStderr(process.getErrorStream(), stderr));

        Throwable[] feederError = new Throwable[1];
        Thread feederThread = Thread.ofVirtual().start(() -> {
            try (OutputStream stdin = process.getOutputStream()) {
                feeder.feed(new OutputStreamSink(stdin));
            } catch (Throwable t) {
                feederError[0] = t;
                if (t instanceof UploadRejectedException) {
                    // Refused input: stop ffmpeg now rather than let it convert half a GIF.
                    process.destroyForcibly();
                }
            }
        });

        boolean[] wantedMore = {true};
        try {
            ByteSource.from(process.getInputStream(), CHUNK).drainTo(new Sink<>() {
                @Override
                public boolean accept(Bytes chunk) throws IOException {
                    wantedMore[0] = stdout.accept(chunk);
                    return wantedMore[0];
                }
            });
            stdout.end();
        } catch (IOException | RuntimeException e) {
            // The consumer failed (a client hung up, an output cap): stop ffmpeg and the feeder.
            process.destroyForcibly();
            joinQuietly(feederThread);
            if (feederError[0] instanceof UploadRejectedException rejected) {
                throw rejected;
            }
            throw e;
        }
        if (!wantedMore[0]) {
            process.destroyForcibly();
        }
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }

        joinQuietly(feederThread);
        joinQuietly(stderrDrain);
        int exit;
        try {
            exit = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted waiting for ffmpeg", e);
        }
        if (feederError[0] instanceof UploadRejectedException rejected) {
            throw rejected;
        }
        if (exit != 0) {
            log.warn("ffmpeg exited {}: {}", exit, tail(stderr));
            throw new IOException("ffmpeg exited " + exit);
        }
        if (feederError[0] != null && !(feederError[0] instanceof IOException)
                && !(feederError[0] instanceof UncheckedIOException)) {
            throw new IOException("feeding ffmpeg failed", feederError[0]);
        }
    }

    private static void drainStderr(InputStream in, StringBuilder into) {
        try {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                synchronized (into) {
                    into.append(new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                    if (into.length() > 8192) {
                        into.delete(0, into.length() - 4096);
                    }
                }
            }
        } catch (IOException ignored) {
            // process gone
        }
    }

    private static String tail(StringBuilder sb) {
        synchronized (sb) {
            String s = sb.toString().strip();
            return s.length() > 1000 ? s.substring(s.length() - 1000) : s;
        }
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A fusio sink over an OutputStream: the terminal stage of a pipeline that ends in a pipe or socket. */
    public static final class OutputStreamSink implements Sink<Bytes> {

        private final OutputStream out;

        public OutputStreamSink(OutputStream out) {
            this.out = out;
        }

        @Override
        public boolean accept(Bytes chunk) throws IOException {
            out.write(chunk.array, chunk.offset, chunk.length);
            return true;
        }

        @Override
        public void end() throws IOException {
            out.flush();
        }
    }
}
