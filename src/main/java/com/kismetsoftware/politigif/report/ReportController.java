/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.report;

import com.kismetsoftware.politigif.ratelimit.RateLimitedException;
import com.kismetsoftware.politigif.ratelimit.RateLimiter;
import com.kismetsoftware.politigif.security.PolitigifPrincipal;
import com.kismetsoftware.politigif.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

/** The Report button on a share page, and the admin queue that answers it. */
@Controller
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/g/{id}/report")
    public String report(@PathVariable String id, @RequestParam(required = false) Reason reason,
                         @AuthenticationPrincipal PolitigifPrincipal principal,
                         HttpServletRequest request, RedirectAttributes redirect) {
        try {
            if (!reportService.report(id, reason, principal == null ? null : principal.userId(),
                    RateLimiter.clientIp(request))) {
                throw new NotFoundException();
            }
            redirect.addFlashAttribute("notice", "Thanks. An admin will take a look.");
        } catch (RateLimitedException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/g/" + id;
    }

    @GetMapping("/admin")
    public String admin() {
        return "redirect:/admin/reports";
    }

    @GetMapping("/admin/reports")
    public String queue(org.springframework.ui.Model model) {
        model.addAttribute("queue", reportService.queue());
        return "admin-reports";
    }

    @PostMapping("/admin/reports/{gifId}/dismiss")
    public String dismiss(@PathVariable String gifId, RedirectAttributes redirect) {
        reportService.dismiss(gifId);
        redirect.addFlashAttribute("notice", "Reports dismissed; the GIF stays.");
        return "redirect:/admin/reports";
    }

    @PostMapping("/admin/gifs/{gifId}/delete")
    public String delete(@PathVariable String gifId, RedirectAttributes redirect) throws IOException {
        reportService.deleteGif(gifId);
        redirect.addFlashAttribute("notice", "GIF deleted.");
        return "redirect:/admin/reports";
    }
}
