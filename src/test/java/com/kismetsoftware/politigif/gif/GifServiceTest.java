/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import com.kismetsoftware.politigif.media.GifTrimPipe;
import com.kismetsoftware.politigif.media.MediaService;
import com.kismetsoftware.politigif.media.UploadRejectedException;
import com.kismetsoftware.politigif.ratelimit.RateLimitPolicy;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import com.kismetsoftware.politigif.storage.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GifServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    private GifRepository gifs;
    private MediaService media;
    private MediaStorage storage;
    private RateLimiter rateLimiter;
    private GifService service;

    @BeforeEach
    void setUp() {
        gifs = mock(GifRepository.class);
        media = mock(MediaService.class);
        storage = mock(MediaStorage.class);
        rateLimiter = mock(RateLimiter.class);
        service = new GifService(gifs, media, storage, rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void anUploadStoresOnlyTheMp4AndRecordsTheTrim() throws IOException {
        InputStream body = new ByteArrayInputStream(new byte[0]);
        byte[] mp4 = {1, 2, 3};
        when(media.convertUpload(body)).thenReturn(
                new MediaService.Converted(mp4, new GifTrimPipe.Result(320, 240, 78, 777, true)));

        Gif gif = service.upload("u1", "The vote", "#senate", body);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), eq(mp4), eq("video/mp4"));
        assertThat(key.getValue()).isEqualTo("gifs/" + gif.getId() + ".mp4");
        verify(gifs).save(gif);
        assertThat(gif.isTrimmed()).isTrue();
        assertThat(gif.getDurationCs()).isEqualTo(777);
        assertThat(gif.getTags()).containsExactly("senate");
        assertThat(gif.getOwnerId()).isEqualTo("u1");
        assertThat(gif.getCreatedAt()).isEqualTo(NOW);
        verify(rateLimiter).require(RateLimitPolicy.UPLOAD_PER_USER, "u1");
    }

    @Test
    void aBadTitleIsRefusedBeforeAnyConversion() throws IOException {
        assertThatThrownBy(() -> service.upload("u1", " ", "", new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(UploadRejectedException.class);
        verify(media, never()).convertUpload(any());
        verify(rateLimiter, never()).require(any(), anyString());
    }

    @Test
    void aRejectedGifStoresNothing() throws IOException {
        InputStream body = new ByteArrayInputStream(new byte[0]);
        when(media.convertUpload(body)).thenThrow(new UploadRejectedException("nope"));

        assertThatThrownBy(() -> service.upload("u1", "t", "", body)).isInstanceOf(UploadRejectedException.class);
        verify(storage, never()).put(anyString(), any(), anyString());
        verify(gifs, never()).save(any());
    }

    @Test
    void aHashQuerySearchesTagsExactly() {
        when(gifs.findPublicByTag(eq("senate"), any(Pageable.class))).thenReturn(Page.empty());

        service.search("  #Senate ", 0);

        verify(gifs).findPublicByTag(eq("senate"), any(Pageable.class));
        verify(gifs, never()).findPublicByTitleMatching(anyString(), any());
    }

    @Test
    void aBareHashFindsNothingWithoutQuerying() {
        assertThat(service.search("#", 0).getContent()).isEmpty();
        verify(gifs, never()).findPublicByTag(anyString(), any());
    }

    @Test
    void otherQueriesSearchTitlesWithTheRegexQuoted() {
        when(gifs.findPublicByTitleMatching(anyString(), any(Pageable.class))).thenReturn(Page.empty());

        service.search("vote (again)", 0);

        verify(gifs).findPublicByTitleMatching(eq("\\Qvote (again)\\E"), any(Pageable.class));
    }

    @Test
    void findRefusesIdsOfTheWrongShapeWithoutQuerying() {
        assertThat(service.find("short")).isEmpty();
        assertThat(service.find(null)).isEmpty();
        verify(gifs, never()).findById(anyString());
    }

    @Test
    void aResetRetiresEachOfTheirGifsAndDeletesNothing() {
        Gif a = new Gif("aaaaaaaaaa", "t", List.of(), "u1", NOW, 2, 2, 10, false, 1);
        Gif b = new Gif("bbbbbbbbbb", "t", List.of(), "u1", NOW, 2, 2, 10, false, 1);
        when(gifs.findByOwnerIdOrderByCreatedAtDesc("u1")).thenReturn(List.of(a, b));

        assertThat(service.retireAllOwnedBy("u1")).isEqualTo(2);

        assertThat(a.isRetiredByReset()).isTrue();
        assertThat(a.getOwnerId()).isNull();
        assertThat(a.getOriginalOwnerId()).isEqualTo("u1");
        verify(gifs).saveAll(List.of(a, b));
        verify(gifs, never()).delete(any());
        org.mockito.Mockito.verifyNoInteractions(storage);
    }

    @Test
    void negativePagesAreTreatedAsTheFirst() {
        when(gifs.findPublic(any(Pageable.class))).thenReturn(Page.empty());

        service.feed(-3);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(gifs).findPublic(page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(Optional.of(page.getValue().getPageSize())).contains(GifService.PAGE_SIZE);
    }
}
