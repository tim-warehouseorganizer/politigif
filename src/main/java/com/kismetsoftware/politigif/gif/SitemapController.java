/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * {@code /sitemap.xml}, generated: the home page plus one {@code <url>} per share page, on the
 * canonical host (the same property the share pages' canonical links use, so they never disagree).
 * One query, no caching: fine at this size.
 */
@RestController
public class SitemapController {

    private final GifService gifService;
    private final String canonicalBase;

    public SitemapController(GifService gifService, @Value("${app.canonical-base-url}") String canonicalBase) {
        this.gifService = gifService;
        this.canonicalBase = canonicalBase.endsWith("/") ? canonicalBase.substring(0, canonicalBase.length() - 1) : canonicalBase;
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    public String sitemap() {
        return render(canonicalBase, gifService.all());
    }

    static String render(String base, List<Gif> gifs) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n")
                .append("  <url><loc>").append(base).append("/</loc></url>\n");
        DateTimeFormatter day = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
        for (Gif gif : gifs) {
            // Ids are [A-Za-z0-9] only, so nothing here needs XML escaping.
            xml.append("  <url><loc>").append(base).append("/g/").append(gif.getId()).append("</loc>");
            if (gif.getCreatedAt() != null) {
                xml.append("<lastmod>").append(day.format(gif.getCreatedAt())).append("</lastmod>");
            }
            xml.append("</url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }
}
