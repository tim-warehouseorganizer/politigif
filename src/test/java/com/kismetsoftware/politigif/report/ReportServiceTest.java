/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.report;

import com.kismetsoftware.politigif.gif.Gif;
import com.kismetsoftware.politigif.gif.GifService;
import com.kismetsoftware.politigif.ratelimit.RateLimitPolicy;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import com.kismetsoftware.politigif.status.ReportStatus;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    private static final Gif GIF = new Gif("abcdEFGH23", "t", List.of(), "owner", NOW, 2, 2, 10, false, 1);

    private ReportRepository reports;
    private MongoTemplate mongo;
    private GifService gifService;
    private RateLimiter rateLimiter;
    private ReportService service;

    @BeforeEach
    void setUp() {
        reports = mock(ReportRepository.class);
        mongo = mock(MongoTemplate.class);
        gifService = mock(GifService.class);
        rateLimiter = mock(RateLimiter.class);
        service = new ReportService(reports, mongo, gifService, rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC));
        when(mongo.updateMulti(any(Query.class), any(Update.class), eq(Report.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    }

    private Document captureUpdate() {
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateMulti(any(Query.class), update.capture(), eq(Report.class));
        return update.getValue().getUpdateObject();
    }

    private Document captureQuery() {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateMulti(query.capture(), any(Update.class), eq(Report.class));
        return query.getValue().getQueryObject();
    }

    @Test
    void aReportOnAnExistingGifIsSavedOpen() {
        when(gifService.find("abcdEFGH23")).thenReturn(Optional.of(GIF));

        assertThat(service.report("abcdEFGH23", Reason.HATE, null, "1.2.3.4")).isTrue();

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reports).save(saved.capture());
        assertThat(saved.getValue().getReason()).isEqualTo(Reason.HATE);
        assertThat(saved.getValue().getStatus()).isEqualTo(ReportStatus.ACTIVE.code());
        verify(rateLimiter).require(RateLimitPolicy.REPORT_PER_IP, "1.2.3.4");
    }

    @Test
    void aMissingReasonIsFiledAsOtherAndTheReporterIsRecordedTwice() {
        when(gifService.find("abcdEFGH23")).thenReturn(Optional.of(GIF));

        service.report("abcdEFGH23", null, "u9", "1.2.3.4");

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reports).save(saved.capture());
        assertThat(saved.getValue().getReason()).isEqualTo(Reason.OTHER);
        assertThat(saved.getValue().getReporterId()).isEqualTo("u9");
        assertThat(saved.getValue().getOriginalReporterId()).isEqualTo("u9");
    }

    @Test
    void aReportOnAnUnknownGifIsNotSaved() {
        when(gifService.find("zzzzzzzzzz")).thenReturn(Optional.empty());

        assertThat(service.report("zzzzzzzzzz", Reason.SPAM, null, "1.2.3.4")).isFalse();
        verify(reports, never()).save(any());
    }

    @Test
    void theQueueShowsOpenReportsOnlyGroupedByGif() {
        Report a1 = new Report("abcdEFGH23", Reason.HATE, null, NOW);
        Report a2 = new Report("abcdEFGH23", Reason.SPAM, null, NOW.minusSeconds(20));
        when(reports.findByStatusOrderByCreatedAtDesc(ReportStatus.ACTIVE.code())).thenReturn(List.of(a1, a2));
        when(gifService.find("abcdEFGH23")).thenReturn(Optional.of(GIF));

        List<ReportService.QueueItem> queue = service.queue();

        assertThat(queue).hasSize(1);
        assertThat(queue.getFirst().reports()).containsExactly(a1, a2);
    }

    @Test
    void dismissingFlagsTheOpenReportsDismissedAndKeepsTheGif() {
        service.dismiss("abcdEFGH23");

        assertThat(captureUpdate().get("$set", Document.class).get("status")).isEqualTo(ReportStatus.DISMISSED.code());
        assertThat(captureQuery()).containsEntry("gifId", "abcdEFGH23").containsEntry("status", ReportStatus.ACTIVE.code());
        verify(gifService, never()).setHiddenByAdmin(any(), eq(true));
    }

    @Test
    void anAdminHideHidesTheGifAndFlagsItsReportsClosed() {
        service.hideGif("abcdEFGH23");

        verify(gifService).setHiddenByAdmin("abcdEFGH23", true);
        assertThat(captureUpdate().get("$set", Document.class).get("status")).isEqualTo(ReportStatus.CLOSED_BY_HIDE.code());
    }

    @Test
    void anAdminUnhideLeavesReportsAlone() {
        service.unhideGif("abcdEFGH23");

        verify(gifService).setHiddenByAdmin("abcdEFGH23", false);
        verify(mongo, never()).updateMulti(any(Query.class), any(Update.class), eq(Report.class));
    }

    @Test
    void anAccountResetDetachesTheReporterButKeepsTheReports() {
        assertThat(service.detachReporter("u9")).isEqualTo(1);

        assertThat(captureQuery()).containsEntry("reporterId", "u9");
        assertThat(captureUpdate().get("$set", Document.class)).containsEntry("reporterId", null);
    }
}
