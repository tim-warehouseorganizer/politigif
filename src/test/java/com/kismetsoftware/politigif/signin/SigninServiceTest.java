/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.signin;

import com.kismetsoftware.politigif.ratelimit.RateLimitPolicy;
import com.kismetsoftware.politigif.ratelimit.RateLimitedException;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import com.kismetsoftware.politigif.status.SigninLinkStatus;
import com.kismetsoftware.politigif.status.UserStatus;
import com.kismetsoftware.politigif.user.User;
import com.kismetsoftware.politigif.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SigninServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    private static final String IP = "203.0.113.7";
    private static final String TOKEN = "3f2b8c1e-6a4d-4c3e-9b7a-1d2e3f4a5b6c";

    private SigninLinkRepository links;
    private UserRepository users;
    private MongoTemplate mongo;
    private RateLimiter rateLimiter;
    private SigninMailer mailer;
    private SigninService service;
    private String nonce;

    @BeforeEach
    void setUp() {
        links = mock(SigninLinkRepository.class);
        users = mock(UserRepository.class);
        mongo = mock(MongoTemplate.class);
        rateLimiter = mock(RateLimiter.class);
        mailer = mock(SigninMailer.class);
        service = new SigninService(links, users, mongo, rateLimiter, mailer,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15));
        nonce = Tokens.newNonce();
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    private SigninLink link(String nonceForLink, Instant expiresAt, Instant usedAt) {
        SigninLink link = new SigninLink(Tokens.sha256(TOKEN), "pat@example.com",
                Tokens.sha256(nonceForLink), IP, NOW.minusSeconds(60), expiresAt);
        link.setId("link-1");
        link.setUsedAt(usedAt);
        return link;
    }

    private void stored(SigninLink link) {
        when(links.findByTokenHash(Tokens.sha256(TOKEN))).thenReturn(Optional.of(link));
    }

    private void consumes(SigninLink link) {
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(SigninLink.class))).thenReturn(link);
    }

    // ---- requestLink ----

    @Test
    void requestingStoresOnlyHashesAndMailsTheLink() {
        service.requestLink("  Pat@Example.com ", nonce, IP);

        ArgumentCaptor<SigninLink> saved = ArgumentCaptor.forClass(SigninLink.class);
        verify(links).save(saved.capture());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailer).deliver(eq("pat@example.com"), token.capture(), eq(15L));
        assertThat(Tokens.isLinkToken(token.getValue())).isTrue();
        assertThat(saved.getValue().getTokenHash()).isEqualTo(Tokens.sha256(token.getValue()));
        assertThat(saved.getValue().getNonceHash()).isEqualTo(Tokens.sha256(nonce));
        assertThat(saved.getValue().getEmail()).isEqualTo("pat@example.com");
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(saved.getValue().getRequestIp()).isEqualTo(IP);
    }

    @Test
    void requestingChecksTheIpThenTheEmailLimit() {
        service.requestLink("pat@example.com", nonce, IP);

        verify(rateLimiter).requireAll(
                new RateLimiter.Dimension(RateLimitPolicy.SIGNIN_LINK_PER_IP, IP),
                new RateLimiter.Dimension(RateLimitPolicy.SIGNIN_LINK_PER_EMAIL, "pat@example.com"));
    }

    @Test
    void aRateLimitedRequestMailsNothing() {
        doThrow(new RateLimitedException("slow down", 60)).when(rateLimiter).requireAll(any(), any());

        assertThatThrownBy(() -> service.requestLink("pat@example.com", nonce, IP))
                .isInstanceOf(RateLimitedException.class);
        verify(links, never()).save(any());
        verify(mailer, never()).deliver(anyString(), anyString(), anyLong());
    }

    @Test
    void aMalformedEmailIsRefusedBeforeTheLimiter() {
        assertThatThrownBy(() -> service.requestLink("not-an-email", nonce, IP))
                .isInstanceOf(IllegalArgumentException.class);
        verify(rateLimiter, never()).requireAll(any(), any());
    }

    @Test
    void aMalformedNonceIsRefused() {
        assertThatThrownBy(() -> service.requestLink("pat@example.com", "short", IP))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mailer, never()).deliver(anyString(), anyString(), anyLong());
    }

    // ---- inspect (the GET: consumes nothing) ----

    @Test
    void inspectIsReadyInTheBrowserThatAsked() {
        stored(link(nonce, NOW.plusSeconds(600), null));

        assertThat(service.inspect(TOKEN, nonce)).isEqualTo(SigninService.LinkState.READY);
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(SigninLink.class));
    }

    @Test
    void inspectInAnotherBrowserSaysSo() {
        stored(link(nonce, NOW.plusSeconds(600), null));

        assertThat(service.inspect(TOKEN, Tokens.newNonce())).isEqualTo(SigninService.LinkState.OTHER_BROWSER);
    }

    @Test
    void inspectWithNoCookieIsAnotherBrowser() {
        stored(link(nonce, NOW.plusSeconds(600), null));

        assertThat(service.inspect(TOKEN, null)).isEqualTo(SigninService.LinkState.OTHER_BROWSER);
    }

    @Test
    void inspectOfAnExpiredLinkIsExpired() {
        stored(link(nonce, NOW, null));

        assertThat(service.inspect(TOKEN, nonce)).isEqualTo(SigninService.LinkState.EXPIRED);
    }

    @Test
    void inspectOfAUsedLinkIsExpired() {
        stored(link(nonce, NOW.plusSeconds(600), NOW.minusSeconds(5)));

        assertThat(service.inspect(TOKEN, nonce)).isEqualTo(SigninService.LinkState.EXPIRED);
    }

    @Test
    void inspectOfAnUnknownOrMalformedTokenIsExpired() {
        when(links.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThat(service.inspect(TOKEN, nonce)).isEqualTo(SigninService.LinkState.EXPIRED);
        assertThat(service.inspect("../../etc", nonce)).isEqualTo(SigninService.LinkState.EXPIRED);
    }

    // ---- redeem (the POST) ----

    @Test
    void redeemingCreatesTheAccountOnFirstUse() {
        SigninLink link = link(nonce, NOW.plusSeconds(600), null);
        stored(link);
        consumes(link);
        when(users.findByEmailAndStatus("pat@example.com", 1)).thenReturn(Optional.empty());

        SigninService.Redemption r = service.redeem(TOKEN, nonce, IP);

        assertThat(r.state()).isEqualTo(SigninService.LinkState.READY);
        assertThat(r.newAccount()).isTrue();
        assertThat(r.user().getEmail()).isEqualTo("pat@example.com");
        assertThat(r.user().hasSeedling()).isFalse();
    }

    @Test
    void redeemingSignsInAnExistingAccount() {
        SigninLink link = link(nonce, NOW.plusSeconds(600), null);
        stored(link);
        consumes(link);
        User existing = new User("pat@example.com", NOW.minusSeconds(86400));
        when(users.findByEmailAndStatus("pat@example.com", 1)).thenReturn(Optional.of(existing));

        SigninService.Redemption r = service.redeem(TOKEN, nonce, IP);

        assertThat(r.user()).isSameAs(existing);
        assertThat(r.newAccount()).isFalse();
        verify(users, never()).save(any());
    }

    @Test
    void redeemingInAnotherBrowserDoesNotBurnTheLink() {
        stored(link(nonce, NOW.plusSeconds(600), null));

        SigninService.Redemption r = service.redeem(TOKEN, Tokens.newNonce(), IP);

        assertThat(r.state()).isEqualTo(SigninService.LinkState.OTHER_BROWSER);
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(SigninLink.class));
    }

    @Test
    void redeemingAnExpiredLinkFails() {
        stored(link(nonce, NOW.minusSeconds(1), null));

        assertThat(service.redeem(TOKEN, nonce, IP).state()).isEqualTo(SigninService.LinkState.EXPIRED);
    }

    @Test
    void redeemingAUsedLinkFails() {
        stored(link(nonce, NOW.plusSeconds(600), NOW.minusSeconds(1)));

        assertThat(service.redeem(TOKEN, nonce, IP).state()).isEqualTo(SigninService.LinkState.EXPIRED);
    }

    @Test
    void losingTheSingleUseRaceFails() {
        stored(link(nonce, NOW.plusSeconds(600), null));
        consumes(null);

        SigninService.Redemption r = service.redeem(TOKEN, nonce, IP);

        assertThat(r.state()).isEqualTo(SigninService.LinkState.EXPIRED);
        verify(users, never()).findByEmailAndStatus(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void aDifferentIpIsLoggedNotBlocked() {
        SigninLink link = link(nonce, NOW.plusSeconds(600), null);
        stored(link);
        consumes(link);
        when(users.findByEmailAndStatus("pat@example.com", 1)).thenReturn(Optional.empty());

        SigninService.Redemption r = service.redeem(TOKEN, nonce, "198.51.100.9");

        assertThat(r.state()).isEqualTo(SigninService.LinkState.READY);
    }

    @Test
    void aDuplicateAccountRaceReturnsTheWinner() {
        SigninLink link = link(nonce, NOW.plusSeconds(600), null);
        stored(link);
        consumes(link);
        User winner = new User("pat@example.com", NOW);
        when(users.findByEmailAndStatus("pat@example.com", 1)).thenReturn(Optional.empty(), Optional.of(winner));
        when(users.save(any(User.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThat(service.redeem(TOKEN, nonce, IP).user()).isSameAs(winner);
    }

    @Test
    void redeemingIsRateLimitedPerIp() {
        doThrow(new RateLimitedException("slow", 30)).when(rateLimiter).require(RateLimitPolicy.SIGNIN_REDEEM_PER_IP, IP);

        assertThatThrownBy(() -> service.redeem(TOKEN, nonce, IP)).isInstanceOf(RateLimitedException.class);
        verify(links, never()).findByTokenHash(anyString());
    }

    // ---- resendFrom (the wrong-browser page) ----

    @Test
    void resendMailsAFreshLinkToTheSameAddressBoundToThisBrowser() {
        stored(link(nonce, NOW.plusSeconds(600), null));
        String here = Tokens.newNonce();

        assertThat(service.resendFrom(TOKEN, here, IP)).isTrue();

        ArgumentCaptor<SigninLink> saved = ArgumentCaptor.forClass(SigninLink.class);
        verify(links).save(saved.capture());
        assertThat(saved.getValue().getNonceHash()).isEqualTo(Tokens.sha256(here));
        verify(mailer).deliver(eq("pat@example.com"), anyString(), eq(15L));
    }

    @Test
    void resendFromAnExpiredLinkDoesNothing() {
        stored(link(nonce, NOW.minusSeconds(1), null));

        assertThat(service.resendFrom(TOKEN, Tokens.newNonce(), IP)).isFalse();
        verify(mailer, never()).deliver(anyString(), anyString(), anyLong());
    }

    // ---- issueLink (account reset) ----

    @Test
    void issueLinkSkipsTheRequestLimits() {
        service.issueLink("pat@example.com", nonce, IP);

        verify(rateLimiter, never()).requireAll(any(), any());
        verify(mailer).deliver(eq("pat@example.com"), anyString(), eq(15L));
    }

    // ---- status flags (no-delete rule) ----

    @Test
    void redeemingFlipsTheLinkFromActiveToUsed() {
        SigninLink link = link(nonce, NOW.plusSeconds(600), null);
        stored(link);
        consumes(link);
        when(users.findByEmailAndStatus("pat@example.com", 1)).thenReturn(Optional.empty());

        service.redeem(TOKEN, nonce, IP);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(q.capture(), u.capture(), any(FindAndModifyOptions.class), eq(SigninLink.class));
        assertThat(q.getValue().getQueryObject()).containsEntry("status", SigninLinkStatus.ACTIVE.code());
        assertThat(u.getValue().getUpdateObject().get("$set", org.bson.Document.class))
                .containsEntry("status", SigninLinkStatus.USED.code());
    }

    @Test
    void aLinkFlaggedExpiredIsExpiredEvenBeforeItsTime() {
        SigninLink link = link(nonce, NOW.plusSeconds(600), null);
        link.setStatus(SigninLinkStatus.EXPIRED.code());
        stored(link);

        assertThat(service.inspect(TOKEN, nonce)).isEqualTo(SigninService.LinkState.EXPIRED);
    }

    @Test
    void aLinkRetiredByAResetCannotBeRedeemed() {
        SigninLink link = link(nonce, NOW.plusSeconds(600), null);
        link.setStatus(SigninLinkStatus.RETIRED_BY_RESET.code());
        stored(link);

        assertThat(service.redeem(TOKEN, nonce, IP).state()).isEqualTo(SigninService.LinkState.EXPIRED);
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(SigninLink.class));
    }

    @Test
    void resetRetiresOnlyThatAddressesActiveLinks() {
        when(mongo.updateMulti(any(Query.class), any(Update.class), eq(SigninLink.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(2, 2L, null));

        assertThat(service.retireActiveLinksFor("pat@example.com")).isEqualTo(2);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateMulti(q.capture(), u.capture(), eq(SigninLink.class));
        assertThat(q.getValue().getQueryObject()).containsEntry("email", "pat@example.com")
                .containsEntry("status", SigninLinkStatus.ACTIVE.code());
        assertThat(u.getValue().getUpdateObject().get("$set", org.bson.Document.class))
                .containsEntry("status", SigninLinkStatus.RETIRED_BY_RESET.code());
    }

    @Test
    void expiryFlagsOverdueActiveLinksInsteadOfDeletingThem() {
        when(mongo.updateMulti(any(Query.class), any(Update.class), eq(SigninLink.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(3, 3L, null));

        assertThat(service.expireOverdueLinks()).isEqualTo(3);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateMulti(q.capture(), u.capture(), eq(SigninLink.class));
        assertThat(q.getValue().getQueryObject()).containsEntry("status", SigninLinkStatus.ACTIVE.code());
        assertThat(q.getValue().getQueryObject().get("expiresAt", org.bson.Document.class)).containsEntry("$lte", NOW);
        assertThat(u.getValue().getUpdateObject().get("$set", org.bson.Document.class))
                .containsEntry("status", SigninLinkStatus.EXPIRED.code());
    }

    @Test
    void onlyAnActiveAccountIsSignedInto() {
        SigninLink link = link(nonce, NOW.plusSeconds(600), null);
        stored(link);
        consumes(link);
        when(users.findByEmailAndStatus("pat@example.com", UserStatus.ACTIVE.code())).thenReturn(Optional.empty());

        SigninService.Redemption r = service.redeem(TOKEN, nonce, IP);

        assertThat(r.newAccount()).isTrue();
        assertThat(r.user().isActive()).isTrue();
    }
}
