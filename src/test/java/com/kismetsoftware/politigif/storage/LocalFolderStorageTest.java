/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFolderStorageTest {

    @TempDir
    Path dir;

    @Test
    void putThenOpenRoundTrips() throws IOException {
        LocalFolderStorage storage = new LocalFolderStorage(dir);

        storage.put("gifs/a.mp4", "hello world".getBytes(StandardCharsets.UTF_8), "video/mp4");

        try (InputStream in = storage.open("gifs/a.mp4")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello world");
        }
        assertThat(storage.size("gifs/a.mp4")).isEqualTo(11);
    }

    @Test
    void aRangeReadsOnlyThoseBytes() throws IOException {
        LocalFolderStorage storage = new LocalFolderStorage(dir);
        storage.put("gifs/a.mp4", "hello world".getBytes(StandardCharsets.UTF_8), "video/mp4");

        try (InputStream in = storage.openRange("gifs/a.mp4", 6, 9)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("worl");
        }
    }

    @Test
    void keysCannotClimbOutOfTheFolder() throws IOException {
        LocalFolderStorage storage = new LocalFolderStorage(dir);

        assertThatThrownBy(() -> storage.resolve("../escape.mp4")).isInstanceOf(IllegalArgumentException.class);
    }
}
