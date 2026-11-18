/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.report;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Someone flagged a GIF. Open until an admin dismisses it or hides the GIF. */
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

    public Report() {
    }

    public Report(String gifId, Reason reason, String reporterId, Instant createdAt) {
        this.gifId = gifId;
        this.reason = reason;
        this.reporterId = reporterId;
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
}
