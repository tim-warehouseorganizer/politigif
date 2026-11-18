/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.signin;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/** Nothing here deletes: links change status (see status.SigninLinkStatus). */
public interface SigninLinkRepository extends MongoRepository<SigninLink, String> {

    Optional<SigninLink> findByTokenHash(String tokenHash);
}
