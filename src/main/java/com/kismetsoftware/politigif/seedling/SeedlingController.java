/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.seedling;

import com.kismetsoftware.politigif.security.PolitigifPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/** {@code /seedling}: choosing the first seedling, right after the account is created. */
@Controller
public class SeedlingController {

    private final SeedlingService seedlingService;

    public SeedlingController(SeedlingService seedlingService) {
        this.seedlingService = seedlingService;
    }

    @GetMapping("/seedling")
    public String form() {
        return "seedling";
    }

    @PostMapping("/seedling")
    public String plant(@AuthenticationPrincipal PolitigifPrincipal principal,
                        @RequestParam(defaultValue = "") String seedling,
                        @RequestParam(defaultValue = "") String confirmation,
                        Model model) {
        Optional<String> problem = seedlingService.plant(principal.userId(), seedling, confirmation);
        if (problem.isPresent()) {
            model.addAttribute("error", problem.get());
            return "seedling";
        }
        return "redirect:/?welcome";
    }
}
