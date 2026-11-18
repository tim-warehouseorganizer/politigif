/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * An account. Created by the first click on a sign-in link; there is no password. The only secret
 * is the seedling (stored as a BCrypt hash), chosen right after the account is created and asked
 * for on every account settings change. A forgotten seedling cannot be recovered; the way out is
 * an account reset, which deletes this document and everything the account owns.
 */
@Document("users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    /** BCrypt hash of the seedling; null until the user has chosen one. */
    private String seedlingHash;

    private Instant createdAt;
    private Instant seedlingChangedAt;

    public User() {
    }

    public User(String email, Instant createdAt) {
        this.email = email;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public String getSeedlingHash() {
        return seedlingHash;
    }

    public void setSeedlingHash(String seedlingHash) {
        this.seedlingHash = seedlingHash;
    }

    public boolean hasSeedling() {
        return seedlingHash != null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSeedlingChangedAt() {
        return seedlingChangedAt;
    }

    public void setSeedlingChangedAt(Instant seedlingChangedAt) {
        this.seedlingChangedAt = seedlingChangedAt;
    }
}
