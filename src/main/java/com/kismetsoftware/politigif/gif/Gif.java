/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * One uploaded GIF. Every GIF is public. Only its MP4 is stored ({@link #storageKey()}); the GIF,
 * WebP and poster are converted from it on each request.
 */
@Document("gifs")
public class Gif {

    @Id
    private String id;

    private String title;

    @Indexed
    private List<String> tags;

    @Indexed
    private String ownerId;

    @Indexed
    private Instant createdAt;

    private int width;
    private int height;
    private int durationCs;
    private boolean trimmed;
    private long mp4Bytes;

    public Gif() {
    }

    public Gif(String id, String title, List<String> tags, String ownerId, Instant createdAt,
               int width, int height, int durationCs, boolean trimmed, long mp4Bytes) {
        this.id = id;
        this.title = title;
        this.tags = tags;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
        this.width = width;
        this.height = height;
        this.durationCs = durationCs;
        this.trimmed = trimmed;
        this.mp4Bytes = mp4Bytes;
    }

    public static String storageKeyFor(String id) {
        return "gifs/" + id + ".mp4";
    }

    public String storageKey() {
        return storageKeyFor(id);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getTags() {
        return tags == null ? List.of() : tags;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** The MP4's dimensions (the GIF's, rounded down to even). */
    public int getWidth() {
        return width - (width % 2);
    }

    public int getHeight() {
        return height - (height % 2);
    }

    public int getDurationCs() {
        return durationCs;
    }

    public boolean isTrimmed() {
        return trimmed;
    }

    public long getMp4Bytes() {
        return mp4Bytes;
    }
}
