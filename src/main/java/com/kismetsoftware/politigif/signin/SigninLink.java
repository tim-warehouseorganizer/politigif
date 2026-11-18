/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.signin;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One emailed sign-in link. Holds hashes only: the token (from the URL) and the nonce (from the
 * cookie set on the browser that asked). Single use: {@code usedAt} is set atomically on redeem.
 * Mongo deletes the row a day after it expires.
 */
@Document("signin_links")
public class SigninLink {

    @Id
    private String id;

    @Indexed(unique = true)
    private String tokenHash;

    @Indexed
    private String email;

    private String nonceHash;
    private String requestIp;
    private Instant createdAt;

    @Indexed(expireAfter = "1d")
    private Instant expiresAt;

    private Instant usedAt;

    public SigninLink() {
    }

    public SigninLink(String tokenHash, String email, String nonceHash, String requestIp,
                      Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.email = email;
        this.nonceHash = nonceHash;
        this.requestIp = requestIp;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean usableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getEmail() {
        return email;
    }

    public String getNonceHash() {
        return nonceHash;
    }

    public String getRequestIp() {
        return requestIp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }
}
