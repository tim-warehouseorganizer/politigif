/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.report;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** Nothing here deletes: reports change status (see status.ReportStatus). */
public interface ReportRepository extends MongoRepository<Report, String> {

    /** The admin queue: reports with {@code status} = ACTIVE (open). */
    List<Report> findByStatusOrderByCreatedAtDesc(int status);
}
