/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import com.kismetsoftware.politigif.media.MediaService;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import com.kismetsoftware.politigif.status.GifStatus;
import com.kismetsoftware.politigif.storage.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * GIFs are never deleted: visibility is the integer {@code status} (GifStatus, the WHO pattern).
 * Who sees each status, every transition, and that no transition touches storage.
 */
class GifVisibilityTest {

    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private static final String ID = "abcdEFGH23";

    private static Gif fresh() {
        return new Gif(ID, "t", List.of(), "owner", NOW, 2, 2, 10, false, 1);
    }

    private static Gif ownerHidden() {
        Gif g = fresh();
        g.ownerSetsHidden(true);
        return g;
    }

    private static Gif adminHidden() {
        Gif g = fresh();
        g.adminHide();
        return g;
    }

    private static Gif retired() {
        Gif g = fresh();
        g.retireByReset();
        return g;
    }

    // ---- who sees what: one test per status, every viewer kind ----

    @Test
    void anActiveGifIsVisibleToEveryone() {
        Gif g = fresh();

        assertThat(g.status()).isEqualTo(GifStatus.ACTIVE);
        assertThat(g.getStatus()).isEqualTo(1);
        assertThat(g.visibleTo(null, false)).isTrue();
        assertThat(g.visibleTo("stranger", false)).isTrue();
        assertThat(g.visibleTo("owner", false)).isTrue();
        assertThat(g.visibleTo("admin", true)).isTrue();
    }

    @Test
    void anOwnerHiddenGifIsVisibleOnlyToItsOwnerAndAdmins() {
        Gif g = ownerHidden();

        assertThat(g.status()).isEqualTo(GifStatus.HIDDEN_BY_OWNER);
        assertThat(g.visibleTo(null, false)).isFalse();
        assertThat(g.visibleTo("stranger", false)).isFalse();
        assertThat(g.visibleTo("owner", false)).isTrue();
        assertThat(g.visibleTo("admin", true)).isTrue();
    }

    @Test
    void anAdminHiddenGifIsVisibleOnlyToItsOwnerAndAdmins() {
        Gif g = adminHidden();

        assertThat(g.status()).isEqualTo(GifStatus.HIDDEN_BY_ADMIN);
        assertThat(g.visibleTo(null, false)).isFalse();
        assertThat(g.visibleTo("stranger", false)).isFalse();
        assertThat(g.visibleTo("owner", false)).isTrue();
        assertThat(g.visibleTo("admin", true)).isTrue();
    }

    @Test
    void aRetiredGifIsVisibleOnlyToAdminsAndKeepsItsOriginalOwner() {
        Gif g = retired();

        assertThat(g.status()).isEqualTo(GifStatus.RETIRED_BY_RESET);
        assertThat(g.getOwnerId()).isNull();
        assertThat(g.getOriginalOwnerId()).isEqualTo("owner");
        assertThat(g.visibleTo(null, false)).isFalse();
        assertThat(g.visibleTo("owner", false)).isFalse();
        assertThat(g.visibleTo("admin", true)).isTrue();
    }

    // ---- transitions ----

    @Test
    void theOwnerUnhidesTheirOwnHide() {
        Gif g = ownerHidden();

        g.ownerSetsHidden(false);

        assertThat(g.status()).isEqualTo(GifStatus.ACTIVE);
    }

    @Test
    void theOwnerCannotUndoAnAdminHide() {
        Gif g = adminHidden();

        g.ownerSetsHidden(false);

        assertThat(g.status()).isEqualTo(GifStatus.HIDDEN_BY_ADMIN);
        assertThat(g.isHidden()).isTrue();
    }

    @Test
    void anOwnerHideUnderAnAdminHideIsRememberedAndRestored() {
        Gif g = adminHidden();

        g.ownerSetsHidden(true);
        assertThat(g.isHiddenByOwner()).isTrue();
        g.adminUnhide();

        assertThat(g.status()).isEqualTo(GifStatus.HIDDEN_BY_OWNER);
    }

    @Test
    void anAdminHideOfAnOwnerHiddenGifRestoresTheOwnersHideWhenLifted() {
        Gif g = ownerHidden();

        g.adminHide();
        assertThat(g.isHiddenByAdmin()).isTrue();
        assertThat(g.isHiddenByOwner()).isTrue();
        g.adminUnhide();

        assertThat(g.status()).isEqualTo(GifStatus.HIDDEN_BY_OWNER);
    }

    @Test
    void anAdminUnhideOfAnActiveUnderneathGifMakesItPublic() {
        Gif g = adminHidden();

        g.adminUnhide();

        assertThat(g.status()).isEqualTo(GifStatus.ACTIVE);
    }

    @Test
    void adminHideAndUnhideAreNoOpsWhereTheyDoNotApply() {
        Gif twice = adminHidden();
        twice.adminHide();
        assertThat(twice.status()).isEqualTo(GifStatus.HIDDEN_BY_ADMIN);

        Gif active = fresh();
        active.adminUnhide();
        assertThat(active.status()).isEqualTo(GifStatus.ACTIVE);

        Gif gone = retired();
        gone.adminHide();
        gone.adminUnhide();
        assertThat(gone.status()).isEqualTo(GifStatus.RETIRED_BY_RESET);
    }

    @Test
    void aRetiredGifHasNoOwnerToHideIt() {
        Gif g = retired();

        assertThatThrownBy(() -> g.ownerSetsHidden(false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retiringAnAdminHiddenGifStillRetiresIt() {
        Gif g = adminHidden();

        g.retireByReset();

        assertThat(g.status()).isEqualTo(GifStatus.RETIRED_BY_RESET);
        assertThat(g.getOriginalOwnerId()).isEqualTo("owner");
    }

    @Test
    void statusCodesMatchTheWhoConventionAndNeverMove() {
        assertThat(GifStatus.DELETED.code()).isZero();
        assertThat(GifStatus.ACTIVE.code()).isEqualTo(1);
        assertThat(GifStatus.HIDDEN_BY_OWNER.code()).isEqualTo(2);
        assertThat(GifStatus.HIDDEN_BY_ADMIN.code()).isEqualTo(3);
        assertThat(GifStatus.RETIRED_BY_RESET.code()).isEqualTo(4);
        assertThat(GifStatus.of(3)).isEqualTo(GifStatus.HIDDEN_BY_ADMIN);
        assertThatThrownBy(() -> GifStatus.of(99)).isInstanceOf(IllegalArgumentException.class);
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
        when(gifs.findById(ID)).thenReturn(Optional.of(ownerHidden()));

        assertThat(service.findVisible(ID, null, false)).isEmpty();
        assertThat(service.findVisible(ID, "stranger", false)).isEmpty();
        assertThat(service.findVisible(ID, "owner", false)).isPresent();
        assertThat(service.findVisible(ID, "admin", true)).isPresent();
    }

    @Test
    void theOwnerHidesThroughTheServiceAndNothingIsDeleted() {
        Gif g = fresh();
        when(gifs.findById(ID)).thenReturn(Optional.of(g));

        assertThat(service.setHiddenByOwner(ID, "owner", true)).contains(g);

        assertThat(g.status()).isEqualTo(GifStatus.HIDDEN_BY_OWNER);
        verify(gifs).save(g);
        verify(gifs, never()).delete(any());
        verifyNoInteractions(storage);
    }

    @Test
    void someoneElseCannotHideIt() {
        Gif g = fresh();
        when(gifs.findById(ID)).thenReturn(Optional.of(g));

        assertThat(service.setHiddenByOwner(ID, "stranger", true)).isEmpty();
        verify(gifs, never()).save(any());
    }

    @Test
    void nobodyOwnsARetiredGif() {
        Gif g = retired();
        when(gifs.findById(ID)).thenReturn(Optional.of(g));

        assertThat(service.setHiddenByOwner(ID, "owner", false)).isEmpty();
        assertThat(g.status()).isEqualTo(GifStatus.RETIRED_BY_RESET);
    }

    @Test
    void hidingAnUnknownGifFindsNothing() {
        when(gifs.findById(anyString())).thenReturn(Optional.empty());

        assertThat(service.setHiddenByOwner(ID, "owner", true)).isEmpty();
        verify(gifs, never()).save(any());
    }

    @Test
    void anAdminHideThroughTheServiceDeletesNothing() {
        Gif g = fresh();
        when(gifs.findById(ID)).thenReturn(Optional.of(g));

        service.setHiddenByAdmin(ID, true);

        assertThat(g.status()).isEqualTo(GifStatus.HIDDEN_BY_ADMIN);
        verify(gifs).save(g);
        verify(gifs, never()).delete(any());
        verifyNoInteractions(storage);
    }

    @Test
    void publicListingsFilterOnTheActiveStatus() {
        assertThat(GifRepository.PUBLIC).isEqualTo("'status': " + GifStatus.ACTIVE.code());
        when(gifs.findAllPublic(any(Sort.class))).thenReturn(List.of(fresh()));

        assertThat(service.allPublic()).hasSize(1);
        verify(gifs, never()).findAll(any(Sort.class));
    }

    @Test
    void theOwnersListIncludesTheirHiddenGifs() {
        when(gifs.findByOwnerIdOrderByCreatedAtDesc("owner")).thenReturn(List.of(ownerHidden(), adminHidden(), fresh()));

        assertThat(service.ownedBy("owner")).hasSize(3);
    }
}
