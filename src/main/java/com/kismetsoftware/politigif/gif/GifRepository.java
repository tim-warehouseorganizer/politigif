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
 * Every public listing goes through a query that excludes hidden GIFs ({@code $ne: true}, so
 * documents written before the hide flags existed count as public). There is deliberately no
 * public finder that returns hidden GIFs.
 */
public interface GifRepository extends MongoRepository<Gif, String> {

    String PUBLIC = "'hiddenByOwner': { $ne: true }, 'hiddenByAdmin': { $ne: true }";

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

    /** The owner's own list, hidden ones included. */
    List<Gif> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    List<Gif> findByOwnerId(String ownerId);
}
