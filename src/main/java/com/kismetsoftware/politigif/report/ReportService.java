/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.report;

import com.kismetsoftware.politigif.gif.Gif;
import com.kismetsoftware.politigif.gif.GifService;
import com.kismetsoftware.politigif.ratelimit.RateLimitPolicy;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reports in, the admin queue out, and the two ways a report is closed. */
@Service
public class ReportService {

    /** One reported GIF in the admin queue: the GIF (null if already gone) and its open reports. */
    public record QueueItem(String gifId, Gif gif, List<Report> reports) {
    }

    private final ReportRepository reports;
    private final GifService gifService;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public ReportService(ReportRepository reports, GifService gifService, RateLimiter rateLimiter, Clock clock) {
        this.reports = reports;
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

    /** Open reports grouped by GIF, most recently reported first. */
    public List<QueueItem> queue() {
        Map<String, List<Report>> byGif = new LinkedHashMap<>();
        for (Report report : reports.findAllByOrderByCreatedAtDesc()) {
            byGif.computeIfAbsent(report.getGifId(), k -> new ArrayList<>()).add(report);
        }
        List<QueueItem> items = new ArrayList<>();
        byGif.forEach((gifId, list) -> items.add(new QueueItem(gifId, gifService.find(gifId).orElse(null), list)));
        return items;
    }

    /** The GIF stays; its reports close. */
    public void dismiss(String gifId) {
        reports.deleteByGifId(gifId);
    }

    /**
     * An admin hides the GIF (a flag its owner cannot undo) and its reports close. GIFs are never
     * deleted: the record and the stored MP4 stay.
     */
    public void hideGif(String gifId) {
        gifService.setHiddenByAdmin(gifId, true);
        reports.deleteByGifId(gifId);
    }

    /** An admin reverses their own hide. The owner's flag, if set, is left alone. */
    public void unhideGif(String gifId) {
        gifService.setHiddenByAdmin(gifId, false);
    }
}
