/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.report;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ReportRepository extends MongoRepository<Report, String> {

    List<Report> findAllByOrderByCreatedAtDesc();

    void deleteByGifId(String gifId);

    void deleteByReporterId(String reporterId);
}
