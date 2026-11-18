/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import com.kismetsoftware.politigif.media.MediaService;
import com.kismetsoftware.politigif.media.UploadRejectedException;
import com.kismetsoftware.politigif.ratelimit.RateLimitPolicy;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import com.kismetsoftware.politigif.storage.MediaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Creating, finding, listing and deleting GIFs. */
@Service
public class GifService {

    private static final Logger log = LoggerFactory.getLogger(GifService.class);

    public static final int MAX_TITLE = 120;
    public static final int PAGE_SIZE = 24;
    private static final String ID_ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int ID_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GifRepository gifs;
    private final MediaService media;
    private final MediaStorage storage;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public GifService(GifRepository gifs, MediaService media, MediaStorage storage,
                      RateLimiter rateLimiter, Clock clock) {
        this.gifs = gifs;
        this.media = media;
        this.storage = storage;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * Converts and stores one upload. Title and tags are checked before a byte of the body is
     * read, so a bad form costs no ffmpeg run.
     *
     * @throws UploadRejectedException with a message for the uploader
     */
    public Gif upload(String ownerId, String rawTitle, String rawTags, InputStream body) throws IOException {
        String title = cleanTitle(rawTitle);
        List<String> tags = Tags.parse(rawTags);
        rateLimiter.require(RateLimitPolicy.UPLOAD_PER_USER, ownerId);

        MediaService.Converted converted = media.convertUpload(body);
        String id = newId();
        storage.put(Gif.storageKeyFor(id), converted.mp4(), "video/mp4");
        var g = converted.gif();
        Gif gif = new Gif(id, title, tags, ownerId, clock.instant(), g.width(), g.height(),
                g.durationCs(), g.trimmed(), converted.mp4().length);
        gifs.save(gif);
        log.info("stored gif {} ({} frames, {} cs, trimmed={}, {} bytes mp4)",
                id, g.frames(), g.durationCs(), g.trimmed(), converted.mp4().length);
        return gif;
    }

    static String cleanTitle(String raw) {
        String title = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (title.isEmpty()) {
            throw new UploadRejectedException("Give your GIF a title.");
        }
        if (title.codePointCount(0, title.length()) > MAX_TITLE) {
            throw new UploadRejectedException("Titles are " + MAX_TITLE + " characters at most.");
        }
        return title;
    }

    public Optional<Gif> find(String id) {
        if (id == null || id.length() != ID_LENGTH) {
            return Optional.empty();
        }
        return gifs.findById(id);
    }

    /**
     * A GIF as this viewer may see it: hidden GIFs exist only for their owner and admins, and look
     * exactly like a missing id to everyone else (no existence oracle).
     */
    public Optional<Gif> findVisible(String id, String viewerId, boolean viewerIsAdmin) {
        return find(id).filter(g -> g.visibleTo(viewerId, viewerIsAdmin));
    }

    /** Public GIFs only, newest first. */
    public Page<Gif> feed(int page) {
        return gifs.findPublic(newestFirst(page));
    }

    /** Public GIFs only. {@code #tag} searches tags exactly; anything else searches titles, case-insensitively. */
    public Page<Gif> search(String query, int page) {
        Pageable pageable = newestFirst(page);
        String q = query == null ? "" : query.strip();
        if (q.startsWith("#")) {
            String tag = Tags.normalize(q.substring(1));
            return tag == null ? Page.empty(pageable) : gifs.findPublicByTag(tag, pageable);
        }
        if (q.length() > MAX_TITLE) {
            q = q.substring(0, MAX_TITLE);
        }
        return gifs.findPublicByTitleMatching(Pattern.quote(q), pageable);
    }

    /** Every public GIF, for the sitemap. */
    public List<Gif> allPublic() {
        return gifs.findAllPublic(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /** The owner's own GIFs, hidden ones included. */
    public List<Gif> ownedBy(String ownerId) {
        return gifs.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    private static Pageable newestFirst(int page) {
        return PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // ---- hiding: GIFs are never deleted (except by the account reset, pending a decision) ----

    /**
     * The owner hides or unhides their own GIF. Needs only a signed-in session, not the seedling.
     * Unhiding clears only the owner's flag: a GIF an admin hid stays hidden.
     *
     * @return the GIF, or empty if it doesn't exist or isn't theirs (the caller answers 404)
     */
    public Optional<Gif> setHiddenByOwner(String id, String ownerId, boolean hidden) {
        Optional<Gif> gif = find(id).filter(g -> g.getOwnerId().equals(ownerId));
        gif.ifPresent(g -> {
            g.setHiddenByOwner(hidden);
            gifs.save(g);
            log.info("gif {} {} by its owner", g.getId(), hidden ? "hidden" : "unhidden");
        });
        return gif;
    }

    /** An admin hides or unhides a GIF. The admin flag is one the owner cannot clear. */
    public Optional<Gif> setHiddenByAdmin(String id, boolean hidden) {
        Optional<Gif> gif = find(id);
        gif.ifPresent(g -> {
            g.setHiddenByAdmin(hidden);
            gifs.save(g);
            log.info("gif {} {} by an admin", g.getId(), hidden ? "hidden" : "unhidden");
        });
        return gif;
    }

    /**
     * Every GIF the account owns, record and stored MP4: the account reset. The ONLY path that
     * removes a GIF; it is kept as-is pending the owner's decision on what a reset does to GIFs.
     */
    public int deleteAllOwnedBy(String ownerId) throws IOException {
        List<Gif> owned = gifs.findByOwnerId(ownerId);
        for (Gif gif : owned) {
            storage.delete(gif.storageKey());
            gifs.delete(gif);
            log.info("deleted gif {} (account reset)", gif.getId());
        }
        return owned.size();
    }

    static String newId() {
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ID_ALPHABET.charAt(RANDOM.nextInt(ID_ALPHABET.length())));
        }
        return sb.toString();
    }
}
