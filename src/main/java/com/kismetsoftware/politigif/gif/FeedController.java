/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** {@code /}: the newest GIFs first, or search results ({@code ?q=title words} / {@code ?q=#tag}). */
@Controller
public class FeedController {

    private final GifService gifService;

    public FeedController(GifService gifService) {
        this.gifService = gifService;
    }

    @GetMapping("/")
    public String feed(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        boolean searching = q != null && !q.isBlank();
        Page<Gif> gifs = searching ? gifService.search(q, page) : gifService.feed(page);
        model.addAttribute("gifs", gifs);
        model.addAttribute("q", searching ? q.strip() : "");
        model.addAttribute("searching", searching);
        return "feed";
    }
}
