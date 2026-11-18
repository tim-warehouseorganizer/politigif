/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.signin;

import com.kismetsoftware.politigif.ratelimit.RateLimitPolicy;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import com.kismetsoftware.politigif.status.SigninLinkStatus;
import com.kismetsoftware.politigif.status.UserStatus;
import com.kismetsoftware.politigif.user.Emails;
import com.kismetsoftware.politigif.user.User;
import com.kismetsoftware.politigif.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Passwordless sign-in: an emailed one-time link that only works in the browser that asked for it.
 *
 * <ol>
 *   <li>{@link #requestLink}: rate-limited per email and per IP; mints a UUID token, binds it to
 *       the browser's nonce (cookie), stores hashes only, mails the link.</li>
 *   <li>{@link #inspect}: the GET of the link. Never consumes it (mail scanners prefetch links);
 *       it only decides which page to show.</li>
 *   <li>{@link #redeem}: the "Sign me in" POST. Single use (atomic), 15 minutes, matching nonce;
 *       creates the account on its first use.</li>
 * </ol>
 * An IP that differs between request and redeem is logged, never blocked: phones hop networks.
 */
@Service
public class SigninService {

    private static final Logger log = LoggerFactory.getLogger(SigninService.class);

    /** What the link page should show. */
    public enum LinkState { READY, OTHER_BROWSER, EXPIRED }

    /** Outcome of the "Sign me in" POST; {@code user} is set only for {@link LinkState#READY}. */
    public record Redemption(LinkState state, User user, boolean newAccount) {
    }

    private final SigninLinkRepository links;
    private final UserRepository users;
    private final MongoTemplate mongo;
    private final RateLimiter rateLimiter;
    private final SigninMailer mailer;
    private final Clock clock;
    private final Duration validity;

    public SigninService(SigninLinkRepository links, UserRepository users, MongoTemplate mongo,
                         RateLimiter rateLimiter, SigninMailer mailer, Clock clock,
                         @Value("${app.signin.link-validity:15m}") Duration validity) {
        this.links = links;
        this.users = users;
        this.mongo = mongo;
        this.rateLimiter = rateLimiter;
        this.mailer = mailer;
        this.clock = clock;
        this.validity = validity;
    }

    /**
     * Mails a sign-in link bound to {@code browserNonce} (the nonce the caller sets as this
     * browser's cookie). Throws {@code RateLimitedException} when over limit.
     *
     * @throws IllegalArgumentException when the address is not shaped like one
     */
    public void requestLink(String rawEmail, String browserNonce, String ip) {
        if (!Emails.looksValid(rawEmail)) {
            throw new IllegalArgumentException("That doesn't look like an email address.");
        }
        String email = Emails.normalize(rawEmail);
        rateLimiter.requireAll(
                new RateLimiter.Dimension(RateLimitPolicy.SIGNIN_LINK_PER_IP, ip),
                new RateLimiter.Dimension(RateLimitPolicy.SIGNIN_LINK_PER_EMAIL, email));
        issueLink(email, browserNonce, ip);
    }

    /**
     * From a link opened in the wrong browser: mails a fresh link to the same address, bound to
     * THIS browser. The address never reaches the page, and the old link is not burned.
     *
     * @return false if the original link is unknown or no longer usable (start over at /signin)
     */
    public boolean resendFrom(String token, String browserNonce, String ip) {
        Optional<SigninLink> link = find(token).filter(l -> l.usableAt(clock.instant()));
        if (link.isEmpty()) {
            return false;
        }
        String email = link.get().getEmail();
        rateLimiter.requireAll(
                new RateLimiter.Dimension(RateLimitPolicy.SIGNIN_LINK_PER_IP, ip),
                new RateLimiter.Dimension(RateLimitPolicy.SIGNIN_LINK_PER_EMAIL, email));
        issueLink(email, browserNonce, ip);
        return true;
    }

    /** Mints and mails a link without the request limits: for the account reset, which needs a session. */
    public void issueLink(String email, String browserNonce, String ip) {
        if (!Tokens.isNonce(browserNonce)) {
            throw new IllegalArgumentException("browser nonce is malformed");
        }
        String token = Tokens.newLinkToken();
        Instant now = clock.instant();
        links.save(new SigninLink(Tokens.sha256(token), email, Tokens.sha256(browserNonce), ip,
                now, now.plus(validity)));
        mailer.deliver(email, token, validity.toMinutes());
    }

    /** The GET of a link: decides the page, consumes nothing. */
    public LinkState inspect(String token, String browserNonce) {
        Optional<SigninLink> link = find(token);
        if (link.isEmpty() || !link.get().usableAt(clock.instant())) {
            return LinkState.EXPIRED;
        }
        return nonceMatches(link.get(), browserNonce) ? LinkState.READY : LinkState.OTHER_BROWSER;
    }

    /** The "Sign me in" POST. A wrong browser does not burn the link. */
    public Redemption redeem(String token, String browserNonce, String ip) {
        rateLimiter.require(RateLimitPolicy.SIGNIN_REDEEM_PER_IP, ip);
        Optional<SigninLink> found = find(token);
        Instant now = clock.instant();
        if (found.isEmpty() || !found.get().usableAt(now)) {
            return new Redemption(LinkState.EXPIRED, null, false);
        }
        SigninLink link = found.get();
        if (!nonceMatches(link, browserNonce)) {
            return new Redemption(LinkState.OTHER_BROWSER, null, false);
        }
        // Single use, atomically: two racing POSTs cannot both find it ACTIVE.
        SigninLink consumed = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(link.getId())
                        .and("status").is(SigninLinkStatus.ACTIVE.code())
                        .and("expiresAt").gt(now)),
                new Update().set("status", SigninLinkStatus.USED.code()).set("usedAt", now),
                FindAndModifyOptions.options().returnNew(true),
                SigninLink.class);
        if (consumed == null) {
            return new Redemption(LinkState.EXPIRED, null, false);
        }
        if (link.getRequestIp() != null && !link.getRequestIp().equals(ip)) {
            log.warn("sign-in link {} redeemed from a different IP than the one that requested it", link.getId());
        }
        Optional<User> existing = users.findByEmailAndStatus(link.getEmail(), UserStatus.ACTIVE.code());
        if (existing.isPresent()) {
            return new Redemption(LinkState.READY, existing.get(), false);
        }
        return new Redemption(LinkState.READY, createAccount(link.getEmail(), now), true);
    }

    private User createAccount(String email, Instant now) {
        try {
            return users.save(new User(email, now));
        } catch (DuplicateKeyException race) {
            // Two links for one address redeemed at once: the other request created it first.
            return users.findByEmailAndStatus(email, UserStatus.ACTIVE.code()).orElseThrow(() -> race);
        }
    }

    /**
     * Account reset: every still-ACTIVE link for the address is flagged RETIRED_BY_RESET (never
     * deleted), so a link mailed before the reset can no longer sign anyone in.
     */
    public long retireActiveLinksFor(String email) {
        return mongo.updateMulti(
                Query.query(Criteria.where("email").is(email).and("status").is(SigninLinkStatus.ACTIVE.code())),
                new Update().set("status", SigninLinkStatus.RETIRED_BY_RESET.code()),
                SigninLink.class).getModifiedCount();
    }

    /** Flags ACTIVE links past their expiry as EXPIRED. Replaces the TTL index that used to delete them. */
    public long expireOverdueLinks() {
        return mongo.updateMulti(
                Query.query(Criteria.where("status").is(SigninLinkStatus.ACTIVE.code())
                        .and("expiresAt").lte(clock.instant())),
                new Update().set("status", SigninLinkStatus.EXPIRED.code()),
                SigninLink.class).getModifiedCount();
    }

    private Optional<SigninLink> find(String token) {
        if (!Tokens.isLinkToken(token)) {
            return Optional.empty();
        }
        return links.findByTokenHash(Tokens.sha256(token));
    }

    private static boolean nonceMatches(SigninLink link, String browserNonce) {
        return Tokens.isNonce(browserNonce)
                && Tokens.hashesEqual(link.getNonceHash(), Tokens.sha256(browserNonce));
    }
}
