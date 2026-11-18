/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.signin;

import com.kismetsoftware.politigif.ratelimit.RateLimitedException;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import com.kismetsoftware.politigif.security.SessionSignIn;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * {@code /signin}: email in, link out. {@code /signin/{token}}: a page with a "Sign me in" button
 * (GET never consumes the link, so mail scanners can't burn it), then the POST that does.
 */
@Controller
public class SigninController {

    private final SigninService signinService;
    private final SigninNonceCookie nonceCookie;
    private final SessionSignIn sessionSignIn;

    public SigninController(SigninService signinService, SigninNonceCookie nonceCookie,
                            SessionSignIn sessionSignIn) {
        this.signinService = signinService;
        this.nonceCookie = nonceCookie;
        this.sessionSignIn = sessionSignIn;
    }

    @GetMapping("/signin")
    public String form() {
        return "signin";
    }

    @PostMapping("/signin")
    public String request(@RequestParam(name = "email", defaultValue = "") String email,
                          HttpServletRequest request, HttpServletResponse response, Model model) {
        try {
            String nonce = nonceCookie.ensure(request, response);
            signinService.requestLink(email, nonce, RateLimiter.clientIp(request));
        } catch (IllegalArgumentException | RateLimitedException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            return "signin";
        }
        return "redirect:/signin/sent";
    }

    @GetMapping("/signin/sent")
    public String sent() {
        return "signin-sent";
    }

    @GetMapping("/signin/{token}")
    public String link(@PathVariable String token, HttpServletRequest request, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("state", signinService.inspect(token, nonceCookie.read(request)).name());
        return "signin-link";
    }

    @PostMapping("/signin/{token}")
    public String redeem(@PathVariable String token, HttpServletRequest request,
                         HttpServletResponse response, Model model) {
        SigninService.Redemption redemption;
        try {
            redemption = signinService.redeem(token, nonceCookie.read(request), RateLimiter.clientIp(request));
        } catch (RateLimitedException e) {
            model.addAttribute("token", token);
            model.addAttribute("state", "READY");
            model.addAttribute("error", e.getMessage());
            return "signin-link";
        }
        if (redemption.state() != SigninService.LinkState.READY) {
            model.addAttribute("token", token);
            model.addAttribute("state", redemption.state().name());
            return "signin-link";
        }
        sessionSignIn.signIn(redemption.user(), request, response);
        return redemption.user().hasSeedling() ? "redirect:/" : "redirect:/seedling";
    }

    /** "Opened in a different browser": send a fresh link bound to this one. */
    @PostMapping("/signin/{token}/resend")
    public String resend(@PathVariable String token, HttpServletRequest request,
                         HttpServletResponse response, Model model) {
        try {
            String nonce = nonceCookie.ensure(request, response);
            if (!signinService.resendFrom(token, nonce, RateLimiter.clientIp(request))) {
                model.addAttribute("token", token);
                model.addAttribute("state", SigninService.LinkState.EXPIRED.name());
                return "signin-link";
            }
        } catch (RateLimitedException e) {
            model.addAttribute("token", token);
            model.addAttribute("state", SigninService.LinkState.OTHER_BROWSER.name());
            model.addAttribute("error", e.getMessage());
            return "signin-link";
        }
        return "redirect:/signin/sent";
    }
}
