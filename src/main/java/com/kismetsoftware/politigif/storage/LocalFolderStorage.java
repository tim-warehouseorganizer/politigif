/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Dev storage: one file per key under a folder. Keys may not climb out of it. */
public class LocalFolderStorage implements MediaStorage {

    private final Path root;

    public LocalFolderStorage(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    @Override
    public void put(String key, byte[] data, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        Files.write(partial, data);
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public InputStream openRange(String key, long first, long last) throws IOException {
        SeekableByteChannel channel = Files.newByteChannel(resolve(key), StandardOpenOption.READ);
        channel.position(first);
        InputStream whole = Channels.newInputStream(channel);
        return new java.io.FilterInputStream(whole) {
            private long left = last - first + 1;

            @Override
            public int read() throws IOException {
                if (left <= 0) {
                    return -1;
                }
                int b = super.read();
                if (b >= 0) {
                    left--;
                }
                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (left <= 0) {
                    return -1;
                }
                int n = super.read(b, off, (int) Math.min(len, left));
                if (n > 0) {
                    left -= n;
                }
                return n;
            }
        };
    }

    @Override
    public long size(String key) throws IOException {
        return Files.size(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("storage key escapes the root: " + key);
        }
        return path;
    }
}
