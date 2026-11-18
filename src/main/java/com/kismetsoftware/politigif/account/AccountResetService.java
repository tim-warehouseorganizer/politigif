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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * The way out of a forgotten seedling. Needs a signed-in session but no seedling: deletes every
 * GIF the account uploaded (stored MP4s included), its reports, its pending sign-in links and the
 * account itself, then mails a fresh sign-in link to the same address, bound to this browser.
 * Redeeming it creates an empty account with the same email, which then chooses a new seedling.
 * Every other session of the old account ends on its next request (AccountStatusFilter).
 */
@Service
public class AccountResetService {

    private static final Logger log = LoggerFactory.getLogger(AccountResetService.class);

    private final UserRepository users;
    private final GifService gifService;
    private final ReportRepository reports;
    private final SigninLinkRepository links;
    private final SigninService signinService;

    public AccountResetService(UserRepository users, GifService gifService, ReportRepository reports,
                               SigninLinkRepository links, SigninService signinService) {
        this.users = users;
        this.gifService = gifService;
        this.reports = reports;
        this.links = links;
        this.signinService = signinService;
    }

    /** @return the address the fresh link went to */
    public String reset(String userId, String browserNonce, String ip) throws IOException {
        User user = users.findById(userId).orElseThrow();
        int deleted = gifService.deleteAllOwnedBy(userId);
        reports.deleteByReporterId(userId);
        links.deleteByEmail(user.getEmail());
        users.delete(user);
        log.info("account {} reset: {} GIFs deleted", userId, deleted);
        signinService.issueLink(user.getEmail(), browserNonce, ip);
        return user.getEmail();
    }
}
