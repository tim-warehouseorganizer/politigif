/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import com.kismetsoftware.politigif.media.MediaService;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import com.kismetsoftware.politigif.storage.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * GIFs are never deleted, only hidden. Who sees a hidden GIF, who can hide and unhide it, and that
 * nothing outside the account reset touches storage or removes a record.
 */
class GifVisibilityTest {

    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private static final String ID = "abcdEFGH23";

    private static Gif gif(boolean byOwner, boolean byAdmin) {
        Gif gif = new Gif(ID, "t", List.of(), "owner", NOW, 2, 2, 10, false, 1);
        gif.setHiddenByOwner(byOwner);
        gif.setHiddenByAdmin(byAdmin);
        return gif;
    }

    // ---- Gif.visibleTo: the full table (guard method, kept uncollapsed) ----

    @Test
    void aPublicGifIsVisibleToEveryone() {
        Gif g = gif(false, false);

        assertThat(g.isHidden()).isFalse();
        assertThat(g.visibleTo(null, false)).isTrue();
        assertThat(g.visibleTo("stranger", false)).isTrue();
        assertThat(g.visibleTo("owner", false)).isTrue();
        assertThat(g.visibleTo("admin", true)).isTrue();
    }

    @Test
    void anOwnerHiddenGifIsVisibleOnlyToItsOwnerAndAdmins() {
        Gif g = gif(true, false);

        assertThat(g.isHidden()).isTrue();
        assertThat(g.visibleTo(null, false)).isFalse();
        assertThat(g.visibleTo("stranger", false)).isFalse();
        assertThat(g.visibleTo("owner", false)).isTrue();
        assertThat(g.visibleTo("admin", true)).isTrue();
    }

    @Test
    void anAdminHiddenGifIsVisibleOnlyToItsOwnerAndAdmins() {
        Gif g = gif(false, true);

        assertThat(g.isHidden()).isTrue();
        assertThat(g.visibleTo(null, false)).isFalse();
        assertThat(g.visibleTo("stranger", false)).isFalse();
        assertThat(g.visibleTo("owner", false)).isTrue();
        assertThat(g.visibleTo("admin", true)).isTrue();
    }

    @Test
    void aGifHiddenByBothIsStillVisibleToItsOwnerAndAdmins() {
        Gif g = gif(true, true);

        assertThat(g.visibleTo(null, false)).isFalse();
        assertThat(g.visibleTo("owner", false)).isTrue();
        assertThat(g.visibleTo("admin", true)).isTrue();
    }

    // ---- GifService ----

    private GifRepository gifs;
    private MediaStorage storage;
    private GifService service;

    @BeforeEach
    void setUp() {
        gifs = mock(GifRepository.class);
        storage = mock(MediaStorage.class);
        service = new GifService(gifs, mock(MediaService.class), storage, mock(RateLimiter.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void findVisibleHidesAHiddenGifFromStrangersExactlyLikeAMissingOne() {
        when(gifs.findById(ID)).thenReturn(Optional.of(gif(true, false)));

        assertThat(service.findVisible(ID, null, false)).isEmpty();
        assertThat(service.findVisible(ID, "stranger", false)).isEmpty();
        assertThat(service.findVisible(ID, "owner", false)).isPresent();
        assertThat(service.findVisible(ID, "admin", true)).isPresent();
    }

    @Test
    void theOwnerCanHideTheirGif() {
        Gif g = gif(false, false);
        when(gifs.findById(ID)).thenReturn(Optional.of(g));

        assertThat(service.setHiddenByOwner(ID, "owner", true)).contains(g);

        assertThat(g.isHiddenByOwner()).isTrue();
        verify(gifs).save(g);
        verifyNoInteractions(storage);
    }

    @Test
    void someoneElseCannotHideIt() {
        Gif g = gif(false, false);
        when(gifs.findById(ID)).thenReturn(Optional.of(g));

        assertThat(service.setHiddenByOwner(ID, "stranger", true)).isEmpty();

        assertThat(g.isHidden()).isFalse();
        verify(gifs, never()).save(any());
    }

    @Test
    void hidingAnUnknownGifFindsNothing() {
        when(gifs.findById(anyString())).thenReturn(Optional.empty());

        assertThat(service.setHiddenByOwner(ID, "owner", true)).isEmpty();
        verify(gifs, never()).save(any());
    }

    @Test
    void theOwnerCanUnhideTheirOwnHide() {
        Gif g = gif(true, false);
        when(gifs.findById(ID)).thenReturn(Optional.of(g));

        service.setHiddenByOwner(ID, "owner", false);

        assertThat(g.isHidden()).isFalse();
    }

    @Test
    void theOwnerCannotUndoAnAdminHide() {
        Gif g = gif(true, true);
        when(gifs.findById(ID)).thenReturn(Optional.of(g));

        service.setHiddenByOwner(ID, "owner", false);

        assertThat(g.isHiddenByOwner()).isFalse();
        assertThat(g.isHiddenByAdmin()).isTrue();
        assertThat(g.isHidden()).isTrue();
    }

    @Test
    void anAdminHideSetsOnlyTheAdminFlagAndDeletesNothing() {
        Gif g = gif(false, false);
        when(gifs.findById(ID)).thenReturn(Optional.of(g));

        service.setHiddenByAdmin(ID, true);

        assertThat(g.isHiddenByAdmin()).isTrue();
        assertThat(g.isHiddenByOwner()).isFalse();
        verify(gifs).save(g);
        verify(gifs, never()).delete(any());
        verifyNoInteractions(storage);
    }

    @Test
    void anAdminUnhideLeavesTheOwnersHideAlone() {
        Gif g = gif(true, true);
        when(gifs.findById(ID)).thenReturn(Optional.of(g));

        service.setHiddenByAdmin(ID, false);

        assertThat(g.isHiddenByAdmin()).isFalse();
        assertThat(g.isHidden()).isTrue();
    }

    @Test
    void theSitemapListsPublicGifsOnly() {
        when(gifs.findAllPublic(any(Sort.class))).thenReturn(List.of(gif(false, false)));

        assertThat(service.allPublic()).hasSize(1);
        verify(gifs).findAllPublic(any(Sort.class));
        verify(gifs, never()).findAll(any(Sort.class));
    }

    @Test
    void theOwnersListIncludesHiddenGifs() {
        List<Gif> mine = List.of(gif(true, false), gif(false, true), gif(false, false));
        when(gifs.findByOwnerIdOrderByCreatedAtDesc("owner")).thenReturn(mine);

        assertThat(service.ownedBy("owner")).hasSize(3);
    }

    @Test
    void thePublicQueriesExcludeBothHideFlags() {
        assertThat(GifRepository.PUBLIC)
                .contains("'hiddenByOwner': { $ne: true }")
                .contains("'hiddenByAdmin': { $ne: true }");
    }

    @Test
    void onlyTheAccountResetRemovesGifs() throws IOException {
        // The one remaining removal path (pending the owner's decision on resets).
        when(gifs.findByOwnerId("owner")).thenReturn(List.of(gif(false, false)));

        service.deleteAllOwnedBy("owner");

        verify(storage).delete("gifs/" + ID + ".mp4");
        verify(gifs).delete(any(Gif.class));
    }
}
