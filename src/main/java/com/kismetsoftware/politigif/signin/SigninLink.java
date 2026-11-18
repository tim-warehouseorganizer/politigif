/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.signin;

import com.kismetsoftware.politigif.status.SigninLinkStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One emailed sign-in link. Holds hashes only: the token (from the URL) and the nonce (from the
 * cookie set on the browser that asked). Single use: {@code status} goes ACTIVE → USED atomically
 * on redeem. Never deleted (no TTL index): {@code SigninLinkExpirer} flips expired ones to EXPIRED.
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

    @Indexed
    private Instant expiresAt;

    private Instant usedAt;

    /** {@link SigninLinkStatus} code (the WHO pattern, see status.Status). */
    @Indexed
    private int status = SigninLinkStatus.ACTIVE.code();

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
        return status == SigninLinkStatus.ACTIVE.code() && usedAt == null && now.isBefore(expiresAt);
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
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
