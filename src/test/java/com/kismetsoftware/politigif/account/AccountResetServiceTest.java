/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.account;

import com.kismetsoftware.politigif.gif.GifService;
import com.kismetsoftware.politigif.report.ReportService;
import com.kismetsoftware.politigif.signin.SigninService;
import com.kismetsoftware.politigif.status.UserStatus;
import com.kismetsoftware.politigif.user.User;
import com.kismetsoftware.politigif.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");

    private UserRepository users;
    private GifService gifs;
    private ReportService reports;
    private SigninService signin;
    private AccountResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        gifs = mock(GifService.class);
        reports = mock(ReportService.class);
        signin = mock(SigninService.class);
        service = new AccountResetService(users, gifs, reports, signin, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User("pat@example.com", Instant.EPOCH);
        user.setId("u1");
        user.setSeedlingHash("$2a$hash");
        when(users.findById("u1")).thenReturn(Optional.of(user));
    }

    @Test
    void aResetFlagsEverythingThenMailsAFreshLinkToTheSameAddress() {
        String sentTo = service.reset("u1", "nonce", "1.2.3.4");

        assertThat(sentTo).isEqualTo("pat@example.com");
        InOrder order = inOrder(gifs, reports, signin, users);
        order.verify(gifs).retireAllOwnedBy("u1");
        order.verify(reports).detachReporter("u1");
        order.verify(signin).retireActiveLinksFor("pat@example.com");
        order.verify(users).save(user);
        order.verify(signin).issueLink("pat@example.com", "nonce", "1.2.3.4");
    }

    @Test
    void theOldAccountRecordIsFlaggedResetNeverDeleted() {
        service.reset("u1", "nonce", "1.2.3.4");

        assertThat(user.getStatus()).isEqualTo(UserStatus.RESET.code());
        assertThat(user.isActive()).isFalse();
        assertThat(user.getRetiredAt()).isEqualTo(NOW);
        assertThat(user.getEmail()).isEqualTo("pat@example.com");
        verify(users, never()).delete(any());
        verify(users, never()).deleteById(any());
    }

    @Test
    void anAlreadyResetAccountCannotBeResetAgain() {
        user.retire(NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.reset("u1", "nonce", "1.2.3.4")).isInstanceOf(NoSuchElementException.class);
        verify(gifs, never()).retireAllOwnedBy(any());
        verify(signin, never()).issueLink(any(), any(), any());
    }
}
