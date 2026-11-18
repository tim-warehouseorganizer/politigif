/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

/**
 * Every public listing filters on {@code status: 1} (ACTIVE, the WHO convention; see
 * {@link com.kismetsoftware.politigif.status.Status}). There is no public finder that returns a
 * hidden or retired GIF, and nothing here deletes.
 */
public interface GifRepository extends MongoRepository<Gif, String> {

    /** The status filter every public query carries: {@code GifStatus.ACTIVE}. */
    String PUBLIC = "'status': 1";

    /** The feed. The caller's Pageable carries the newest-first sort. */
    @Query("{ " + PUBLIC + " }")
    Page<Gif> findPublic(Pageable pageable);

    @Query("{ " + PUBLIC + " }")
    List<Gif> findAllPublic(Sort sort);

    @Query("{ 'tags': ?0, " + PUBLIC + " }")
    Page<Gif> findPublicByTag(String tag, Pageable pageable);

    /** Case-insensitive title match; the caller passes a regex-quoted pattern. */
    @Query("{ 'title': { $regex: ?0, $options: 'i' }, " + PUBLIC + " }")
    Page<Gif> findPublicByTitleMatching(String quotedPattern, Pageable pageable);

    /** The owner's own list, hidden ones included. Retired GIFs have no owner and never appear. */
    List<Gif> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
}
