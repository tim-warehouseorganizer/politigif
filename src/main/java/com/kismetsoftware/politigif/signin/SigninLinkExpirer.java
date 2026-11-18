/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.signin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Flags expired sign-in links EXPIRED every few minutes. This replaces the TTL index that used to
 * delete them: under the no-delete rule nothing is removed, only flagged. A link past its expiry is
 * already unusable (SigninLink.usableAt checks the time too); this keeps the stored status honest.
 */
@Component
public class SigninLinkExpirer {

    private static final Logger log = LoggerFactory.getLogger(SigninLinkExpirer.class);

    private final SigninService signinService;

    public SigninLinkExpirer(SigninService signinService) {
        this.signinService = signinService;
    }

    @Scheduled(fixedDelayString = "${app.signin.expire-sweep:PT5M}", initialDelayString = "PT1M")
    public void expire() {
        long flagged = signinService.expireOverdueLinks();
        if (flagged > 0) {
            log.info("flagged {} sign-in links EXPIRED", flagged);
        }
    }
}
