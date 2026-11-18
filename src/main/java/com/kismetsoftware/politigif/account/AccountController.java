/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.account;

import com.kismetsoftware.politigif.gif.GifService;
import com.kismetsoftware.politigif.ratelimit.RateLimitedException;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import com.kismetsoftware.politigif.security.PolitigifPrincipal;
import com.kismetsoftware.politigif.security.SessionSignIn;
import com.kismetsoftware.politigif.seedling.SeedlingService;
import com.kismetsoftware.politigif.signin.SigninNonceCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Optional;

/**
 * {@code /account}. Every settings change needs the current seedling; today the one setting is
 * the seedling itself. The reset is the exception: it needs no seedling, because it is what you do
 * when you have forgotten it, and it keeps nothing.
 */
@Controller
public class AccountController {

    private final SeedlingService seedlingService;
    private final AccountResetService resetService;
    private final SigninNonceCookie nonceCookie;
    private final SessionSignIn sessionSignIn;
    private final GifService gifService;

    public AccountController(SeedlingService seedlingService, AccountResetService resetService,
                             SigninNonceCookie nonceCookie, SessionSignIn sessionSignIn, GifService gifService) {
        this.gifService = gifService;
        this.seedlingService = seedlingService;
        this.resetService = resetService;
        this.nonceCookie = nonceCookie;
        this.sessionSignIn = sessionSignIn;
    }

    @GetMapping("/account")
    public String account(@AuthenticationPrincipal PolitigifPrincipal principal, Model model) {
        model.addAttribute("email", principal.email());
        // The owner's own GIFs, hidden ones included, each with its Hide / Unhide toggle.
        model.addAttribute("gifs", gifService.ownedBy(principal.userId()));
        return "account";
    }

    @PostMapping("/account/seedling")
    public String changeSeedling(@AuthenticationPrincipal PolitigifPrincipal principal,
                                 @RequestParam(defaultValue = "") String current,
                                 @RequestParam(defaultValue = "") String seedling,
                                 @RequestParam(defaultValue = "") String confirmation,
                                 HttpServletRequest request, RedirectAttributes redirect) {
        try {
            Optional<String> problem = seedlingService.change(principal.userId(), RateLimiter.clientIp(request),
                    current, seedling, confirmation);
            if (problem.isPresent()) {
                redirect.addFlashAttribute("error", problem.get());
            } else {
                redirect.addFlashAttribute("notice", "Your seedling is changed.");
            }
        } catch (RateLimitedException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/account";
    }

    @PostMapping("/account/reset")
    public String reset(@AuthenticationPrincipal PolitigifPrincipal principal,
                        @RequestParam(defaultValue = "false") boolean understood,
                        HttpServletRequest request, HttpServletResponse response,
                        RedirectAttributes redirect) throws IOException {
        if (!understood) {
            redirect.addFlashAttribute("error", "Tick the box to confirm: a reset deletes all your GIFs.");
            return "redirect:/account";
        }
        String nonce = nonceCookie.ensure(request, response);
        resetService.reset(principal.userId(), nonce, RateLimiter.clientIp(request));
        sessionSignIn.signOut(request);
        return "redirect:/signin/sent?reset";
    }
}
