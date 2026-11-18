/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.account;

import com.kismetsoftware.politigif.gif.GifService;
import com.kismetsoftware.politigif.report.ReportService;
import com.kismetsoftware.politigif.signin.SigninService;
import com.kismetsoftware.politigif.user.User;
import com.kismetsoftware.politigif.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * The way out of a forgotten seedling. Needs a signed-in session but no seedling. Under the
 * no-delete rule (status.Status) nothing is removed:
 * <ul>
 *   <li>the account's GIFs are flagged RETIRED_BY_RESET (hidden from everyone but admins) and
 *       detached from it, each keeping the original owner id; their MP4s stay;</li>
 *   <li>reports it filed stay, detached from it (original reporter id kept);</li>
 *   <li>its still-ACTIVE sign-in links are flagged RETIRED_BY_RESET;</li>
 *   <li>the account record is flagged RESET, never deleted;</li>
 *   <li>a fresh sign-in link goes to the same email, bound to this browser. Redeeming it creates a
 *       new, empty ACTIVE account on that email, which then chooses a new seedling.</li>
 * </ul>
 * Every other session of the old account ends on its next request (AccountStatusFilter).
 */
@Service
public class AccountResetService {

    private static final Logger log = LoggerFactory.getLogger(AccountResetService.class);

    private final UserRepository users;
    private final GifService gifService;
    private final ReportService reportService;
    private final SigninService signinService;
    private final Clock clock;

    public AccountResetService(UserRepository users, GifService gifService, ReportService reportService,
                               SigninService signinService, Clock clock) {
        this.users = users;
        this.gifService = gifService;
        this.reportService = reportService;
        this.signinService = signinService;
        this.clock = clock;
    }

    /** @return the address the fresh link went to */
    public String reset(String userId, String browserNonce, String ip) {
        User user = users.findById(userId).filter(User::isActive).orElseThrow();
        int retired = gifService.retireAllOwnedBy(userId);
        reportService.detachReporter(userId);
        signinService.retireActiveLinksFor(user.getEmail());
        user.retire(clock.instant());
        users.save(user);
        log.info("account {} reset: {} GIFs retired, record flagged RESET", userId, retired);
        signinService.issueLink(user.getEmail(), browserNonce, ip);
        return user.getEmail();
    }
}
