/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.report;

import com.kismetsoftware.politigif.status.ReportStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Someone flagged a GIF. {@code status} ACTIVE = open; an admin dismissing it or hiding the GIF
 * flips the status. Never deleted.
 */
@Document("reports")
public class Report {

    @Id
    private String id;

    @Indexed
    private String gifId;

    private Reason reason;

    /** The signed-in reporter, or null for a signed-out one. */
    @Indexed
    private String reporterId;

    private Instant createdAt;

    /** {@link ReportStatus} code (the WHO pattern, see status.Status). */
    @Indexed
    private int status = ReportStatus.ACTIVE.code();

    /** Who filed it, kept when an account reset detaches the report from the account. */
    private String originalReporterId;

    public Report() {
    }

    public Report(String gifId, Reason reason, String reporterId, Instant createdAt) {
        this.gifId = gifId;
        this.reason = reason;
        this.reporterId = reporterId;
        this.originalReporterId = reporterId;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getGifId() {
        return gifId;
    }

    public Reason getReason() {
        return reason;
    }

    public String getReporterId() {
        return reporterId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getStatus() {
        return status;
    }

    public String getOriginalReporterId() {
        return originalReporterId;
    }
}
