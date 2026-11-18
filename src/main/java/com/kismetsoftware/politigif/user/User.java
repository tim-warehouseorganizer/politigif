/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.user;

import org.springframework.data.annotation.Id;
import com.kismetsoftware.politigif.status.UserStatus;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * An account. Created by the first click on a sign-in link; there is no password. The only secret
 * is the seedling (stored as a BCrypt hash), chosen right after the account is created and asked
 * for on every account settings change. A forgotten seedling cannot be recovered; the way out is
 * an account reset, which flags this record RESET (never deletes it) and starts a fresh account.
 */
@Document("users")
// One ACTIVE account per email. Unique only among ACTIVE rows: a reset leaves the old record in
// place (status RESET) and a fresh ACTIVE account takes the same address.
@CompoundIndex(name = "email_active_unique", def = "{'email': 1}", unique = true, partialFilter = "{'status': 1}")
public class User {

    @Id
    private String id;

    @Indexed
    private String email;

    /** {@link UserStatus} code (the WHO pattern, see status.Status). Never deleted. */
    private int status = UserStatus.ACTIVE.code();

    /** When an account reset retired this record. */
    private Instant retiredAt;

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

    public boolean isActive() {
        return status == UserStatus.ACTIVE.code();
    }

    public int getStatus() {
        return status;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }

    /** Account reset: the record stays, flagged RESET. */
    public void retire(Instant when) {
        status = UserStatus.RESET.code();
        retiredAt = when;
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
