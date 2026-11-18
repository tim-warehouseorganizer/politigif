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

    /** The owner hid it; the owner can unhide it. */
    private boolean hiddenByOwner;

    /** An admin hid it; the owner cannot undo this. */
    private boolean hiddenByAdmin;

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

    public boolean isHiddenByOwner() {
        return hiddenByOwner;
    }

    public void setHiddenByOwner(boolean hiddenByOwner) {
        this.hiddenByOwner = hiddenByOwner;
    }

    public boolean isHiddenByAdmin() {
        return hiddenByAdmin;
    }

    public void setHiddenByAdmin(boolean hiddenByAdmin) {
        this.hiddenByAdmin = hiddenByAdmin;
    }

    /** Hidden from the public by anyone: out of the feed, search and sitemap; 404 to strangers. */
    public boolean isHidden() {
        return hiddenByOwner || hiddenByAdmin;
    }

    /**
     * The visibility rule: a public GIF is visible to everyone; a hidden one only to its owner and
     * to admins. {@code viewerId} is null for a signed-out visitor.
     */
    public boolean visibleTo(String viewerId, boolean viewerIsAdmin) {
        return !isHidden() || viewerIsAdmin || (viewerId != null && viewerId.equals(ownerId));
    }

    public long getMp4Bytes() {
        return mp4Bytes;
    }
}
