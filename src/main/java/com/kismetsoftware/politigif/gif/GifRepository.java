/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface GifRepository extends MongoRepository<Gif, String> {

    Page<Gif> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Gif> findByTagsOrderByCreatedAtDesc(String tag, Pageable pageable);

    /** Case-insensitive title match; the caller passes a regex-quoted pattern. */
    @Query("{ 'title': { $regex: ?0, $options: 'i' } }")
    Page<Gif> findByTitleMatching(String quotedPattern, Pageable pageable);

    List<Gif> findByOwnerId(String ownerId);
}
