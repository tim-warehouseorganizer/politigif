/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.report;

import com.kismetsoftware.politigif.gif.Gif;
import com.kismetsoftware.politigif.gif.GifService;
import com.kismetsoftware.politigif.ratelimit.RateLimitPolicy;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    private static final Gif GIF = new Gif("abcdEFGH23", "t", List.of(), "owner", NOW, 2, 2, 10, false, 1);

    private ReportRepository reports;
    private GifService gifService;
    private RateLimiter rateLimiter;
    private ReportService service;

    @BeforeEach
    void setUp() {
        reports = mock(ReportRepository.class);
        gifService = mock(GifService.class);
        rateLimiter = mock(RateLimiter.class);
        service = new ReportService(reports, gifService, rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aReportOnAnExistingGifIsSaved() {
        when(gifService.find("abcdEFGH23")).thenReturn(Optional.of(GIF));

        assertThat(service.report("abcdEFGH23", Reason.HATE, null, "1.2.3.4")).isTrue();

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reports).save(saved.capture());
        assertThat(saved.getValue().getReason()).isEqualTo(Reason.HATE);
        assertThat(saved.getValue().getReporterId()).isNull();
        verify(rateLimiter).require(RateLimitPolicy.REPORT_PER_IP, "1.2.3.4");
    }

    @Test
    void aMissingReasonIsFiledAsOther() {
        when(gifService.find("abcdEFGH23")).thenReturn(Optional.of(GIF));

        service.report("abcdEFGH23", null, "u9", "1.2.3.4");

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reports).save(saved.capture());
        assertThat(saved.getValue().getReason()).isEqualTo(Reason.OTHER);
        assertThat(saved.getValue().getReporterId()).isEqualTo("u9");
    }

    @Test
    void aReportOnAnUnknownGifIsNotSaved() {
        when(gifService.find("zzzzzzzzzz")).thenReturn(Optional.empty());

        assertThat(service.report("zzzzzzzzzz", Reason.SPAM, null, "1.2.3.4")).isFalse();
        verify(reports, never()).save(any());
    }

    @Test
    void theQueueGroupsReportsByGif() {
        Report a1 = new Report("abcdEFGH23", Reason.HATE, null, NOW);
        Report b1 = new Report("gone000000", Reason.SPAM, null, NOW.minusSeconds(10));
        Report a2 = new Report("abcdEFGH23", Reason.SPAM, null, NOW.minusSeconds(20));
        when(reports.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(a1, b1, a2));
        when(gifService.find("abcdEFGH23")).thenReturn(Optional.of(GIF));
        when(gifService.find("gone000000")).thenReturn(Optional.empty());

        List<ReportService.QueueItem> queue = service.queue();

        assertThat(queue).hasSize(2);
        assertThat(queue.get(0).gif()).isSameAs(GIF);
        assertThat(queue.get(0).reports()).containsExactly(a1, a2);
        assertThat(queue.get(1).gif()).isNull();
    }

    @Test
    void deletingTheGifClosesItsReports() throws IOException {
        when(gifService.find("abcdEFGH23")).thenReturn(Optional.of(GIF));

        service.deleteGif("abcdEFGH23");

        verify(gifService).delete(GIF);
        verify(reports).deleteByGifId("abcdEFGH23");
    }

    @Test
    void deletingAnAlreadyGoneGifStillClosesItsReports() throws IOException {
        when(gifService.find("gone000000")).thenReturn(Optional.empty());

        service.deleteGif("gone000000");

        verify(gifService, never()).delete(any());
        verify(reports).deleteByGifId("gone000000");
    }

    @Test
    void dismissingKeepsTheGif() {
        service.dismiss("abcdEFGH23");

        verify(reports).deleteByGifId("abcdEFGH23");
        verify(gifService, never()).find(any());
    }
}
