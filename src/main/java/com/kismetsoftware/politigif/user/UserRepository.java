/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.user;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/** Lookups filter by status: a RESET account is never found by email again. Nothing here deletes. */
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmailAndStatus(String email, int status);
}
