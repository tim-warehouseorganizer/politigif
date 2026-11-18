/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.account;

import com.kismetsoftware.politigif.gif.GifService;
import com.kismetsoftware.politigif.report.ReportRepository;
import com.kismetsoftware.politigif.signin.SigninLinkRepository;
import com.kismetsoftware.politigif.signin.SigninService;
import com.kismetsoftware.politigif.user.User;
import com.kismetsoftware.politigif.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountResetServiceTest {

    @Test
    void aResetDeletesEverythingThenMailsAFreshLinkToTheSameAddress() throws IOException {
        UserRepository users = mock(UserRepository.class);
        GifService gifs = mock(GifService.class);
        ReportRepository reports = mock(ReportRepository.class);
        SigninLinkRepository links = mock(SigninLinkRepository.class);
        SigninService signin = mock(SigninService.class);
        User user = new User("pat@example.com", Instant.EPOCH);
        user.setId("u1");
        user.setSeedlingHash("$2a$hash");
        when(users.findById("u1")).thenReturn(Optional.of(user));
        AccountResetService service = new AccountResetService(users, gifs, reports, links, signin);

        String sentTo = service.reset("u1", "nonce", "1.2.3.4");

        assertThat(sentTo).isEqualTo("pat@example.com");
        InOrder order = inOrder(gifs, reports, links, users, signin);
        order.verify(gifs).deleteAllOwnedBy("u1");
        order.verify(reports).deleteByReporterId("u1");
        order.verify(links).deleteByEmail("pat@example.com");
        order.verify(users).delete(user);
        order.verify(signin).issueLink("pat@example.com", "nonce", "1.2.3.4");
    }
}
