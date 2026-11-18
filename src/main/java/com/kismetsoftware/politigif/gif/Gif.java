/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import com.kismetsoftware.politigif.status.GifStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * One uploaded GIF. Only its MP4 is stored ({@link #storageKey()}); the GIF, WebP and poster are
 * converted from it on each request. Never deleted: visibility is the integer {@code status}
 * ({@link GifStatus}, the WHO pattern described in {@link com.kismetsoftware.politigif.status.Status}).
 */
@Document("gifs")
public class Gif {

    @Id
    private String id;

    private String title;

    @Indexed
    private List<String> tags;

    /** The current owner; null once an account reset has detached it. */
    @Indexed
    private String ownerId;

    /** Who uploaded it, kept when an account reset detaches the GIF from the account. */
    @Indexed
    private String originalOwnerId;

    @Indexed
    private Instant createdAt;

    private int width;
    private int height;
    private int durationCs;
    private boolean trimmed;
    private long mp4Bytes;

    /** {@link GifStatus} code. Only ACTIVE is public. */
    @Indexed
    private int status = GifStatus.ACTIVE.code();

    /** While HIDDEN_BY_ADMIN: the status to restore on admin unhide (the owner's own choice). */
    private Integer statusBeforeAdminHide;

    public Gif() {
    }

    public Gif(String id, String title, List<String> tags, String ownerId, Instant createdAt,
               int width, int height, int durationCs, boolean trimmed, long mp4Bytes) {
        this.id = id;
        this.title = title;
        this.tags = tags;
        this.ownerId = ownerId;
        this.originalOwnerId = ownerId;
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

    public String getOriginalOwnerId() {
        return originalOwnerId;
    }

    // ---- status: the only way a GIF ever "goes away" ----

    public GifStatus status() {
        return GifStatus.of(status);
    }

    /** The stored code, for templates and logs. */
    public int getStatus() {
        return status;
    }

    /** Not public: out of the feed, search and sitemap, and 404 to strangers. */
    public boolean isHidden() {
        return status != GifStatus.ACTIVE.code();
    }

    public boolean isHiddenByAdmin() {
        return status == GifStatus.HIDDEN_BY_ADMIN.code();
    }

    /** The owner's own choice, which survives an admin hide (and is restored when it is lifted). */
    public boolean isHiddenByOwner() {
        return status == GifStatus.HIDDEN_BY_OWNER.code()
                || (isHiddenByAdmin() && statusBeforeAdminHide != null
                && statusBeforeAdminHide == GifStatus.HIDDEN_BY_OWNER.code());
    }

    public boolean isRetiredByReset() {
        return status == GifStatus.RETIRED_BY_RESET.code();
    }

    /**
     * The visibility rule. ACTIVE → everyone. Hidden by owner or admin → its owner and admins.
     * Retired by an account reset → admins only (it has no owner any more).
     * {@code viewerId} is null for a signed-out visitor.
     */
    public boolean visibleTo(String viewerId, boolean viewerIsAdmin) {
        if (!isHidden() || viewerIsAdmin) {
            return true;
        }
        return !isRetiredByReset() && viewerId != null && viewerId.equals(ownerId);
    }

    /**
     * The owner hides or unhides. Under an admin hide this only records the owner's choice (to be
     * restored when the admin hide is lifted): the GIF stays hidden. A retired GIF has no owner.
     */
    public void ownerSetsHidden(boolean hidden) {
        int wanted = hidden ? GifStatus.HIDDEN_BY_OWNER.code() : GifStatus.ACTIVE.code();
        switch (status()) {
            case ACTIVE, HIDDEN_BY_OWNER -> status = wanted;
            case HIDDEN_BY_ADMIN -> statusBeforeAdminHide = wanted;
            default -> throw new IllegalStateException("gif " + id + " has no owner to hide it");
        }
    }

    /** An admin hides: remembers the status underneath so an admin unhide can restore it. */
    public void adminHide() {
        if (status == GifStatus.ACTIVE.code() || status == GifStatus.HIDDEN_BY_OWNER.code()) {
            statusBeforeAdminHide = status;
            status = GifStatus.HIDDEN_BY_ADMIN.code();
        }
    }

    /** An admin lifts their hide: back to whatever the owner had chosen. */
    public void adminUnhide() {
        if (isHiddenByAdmin()) {
            status = statusBeforeAdminHide == null ? GifStatus.ACTIVE.code() : statusBeforeAdminHide;
            statusBeforeAdminHide = null;
        }
    }

    /** Account reset: hidden, detached from the account, the original owner kept. The MP4 stays. */
    public void retireByReset() {
        if (originalOwnerId == null) {
            originalOwnerId = ownerId;
        }
        ownerId = null;
        statusBeforeAdminHide = null;
        status = GifStatus.RETIRED_BY_RESET.code();
    }

    public long getMp4Bytes() {
        return mp4Bytes;
    }
}
