/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.report;

import com.kismetsoftware.politigif.gif.Gif;
import com.kismetsoftware.politigif.gif.GifService;
import com.kismetsoftware.politigif.ratelimit.RateLimitPolicy;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import com.kismetsoftware.politigif.status.ReportStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports in, the admin queue out, and the ways a report is closed. Closing a report flips its
 * {@code status}; reports are never deleted (see status.Status).
 */
@Service
public class ReportService {

    /** One reported GIF in the admin queue: the GIF (null if the id no longer resolves) and its open reports. */
    public record QueueItem(String gifId, Gif gif, List<Report> reports) {
    }

    private final ReportRepository reports;
    private final MongoTemplate mongo;
    private final GifService gifService;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public ReportService(ReportRepository reports, MongoTemplate mongo, GifService gifService,
                         RateLimiter rateLimiter, Clock clock) {
        this.reports = reports;
        this.mongo = mongo;
        this.gifService = gifService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * Files a report. Anyone may report; signed-out reports are limited per IP.
     *
     * @return false when the GIF does not exist
     */
    public boolean report(String gifId, Reason reason, String reporterId, String ip) {
        rateLimiter.require(RateLimitPolicy.REPORT_PER_IP, ip);
        if (gifService.find(gifId).isEmpty()) {
            return false;
        }
        reports.save(new Report(gifId, reason == null ? Reason.OTHER : reason, reporterId, clock.instant()));
        return true;
    }

    /** Open (ACTIVE) reports grouped by GIF, most recently reported first. */
    public List<QueueItem> queue() {
        Map<String, List<Report>> byGif = new LinkedHashMap<>();
        for (Report report : reports.findByStatusOrderByCreatedAtDesc(ReportStatus.ACTIVE.code())) {
            byGif.computeIfAbsent(report.getGifId(), k -> new ArrayList<>()).add(report);
        }
        List<QueueItem> items = new ArrayList<>();
        byGif.forEach((gifId, list) -> items.add(new QueueItem(gifId, gifService.find(gifId).orElse(null), list)));
        return items;
    }

    /** The GIF stays; its open reports are flagged DISMISSED. */
    public void dismiss(String gifId) {
        close(gifId, ReportStatus.DISMISSED);
    }

    /** An admin hides the GIF (a hide its owner cannot undo); its open reports are flagged CLOSED_BY_HIDE. */
    public void hideGif(String gifId) {
        gifService.setHiddenByAdmin(gifId, true);
        close(gifId, ReportStatus.CLOSED_BY_HIDE);
    }

    /** An admin reverses their own hide; the owner's choice underneath is restored. */
    public void unhideGif(String gifId) {
        gifService.setHiddenByAdmin(gifId, false);
    }

    /**
     * Account reset: reports the account filed stay (and stay open), detached from the account; the
     * original reporter id is kept on each report.
     */
    public long detachReporter(String reporterId) {
        return mongo.updateMulti(
                Query.query(Criteria.where("reporterId").is(reporterId)),
                new Update().set("reporterId", null),
                Report.class).getModifiedCount();
    }

    private void close(String gifId, ReportStatus to) {
        mongo.updateMulti(
                Query.query(Criteria.where("gifId").is(gifId).and("status").is(ReportStatus.ACTIVE.code())),
                new Update().set("status", to.code()),
                Report.class);
    }
}
