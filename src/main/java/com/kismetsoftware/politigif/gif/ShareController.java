/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import com.kismetsoftware.politigif.media.MediaService;
import com.kismetsoftware.politigif.security.PolitigifPrincipal;
import com.kismetsoftware.politigif.storage.MediaStorage;
import com.kismetsoftware.politigif.web.NotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.RequestHeader;

import java.io.IOException;
import java.io.InputStream;

/**
 * The share page {@code /g/{id}} and its media: {@code .mp4} (the stored file, with Range support
 * because Safari will not play a video without it), and {@code .gif}, {@code .webp},
 * {@code /poster.jpg} converted fresh by ffmpeg on every request.
 */
@Controller
public class ShareController {

    private final GifService gifService;
    private final MediaService mediaService;
    private final MediaStorage storage;
    private final String canonicalBase;

    public ShareController(GifService gifService, MediaService mediaService, MediaStorage storage,
                           @Value("${app.canonical-base-url}") String canonicalBase) {
        this.gifService = gifService;
        this.mediaService = mediaService;
        this.storage = storage;
        this.canonicalBase = canonicalBase.endsWith("/") ? canonicalBase.substring(0, canonicalBase.length() - 1) : canonicalBase;
    }

    @GetMapping("/g/{id}")
    public String share(@PathVariable String id, @AuthenticationPrincipal PolitigifPrincipal principal, Model model) {
        Gif gif = visible(id, principal);
        model.addAttribute("gif", gif);
        model.addAttribute("canonical", canonicalBase + "/g/" + gif.getId());
        model.addAttribute("canonicalBase", canonicalBase);
        model.addAttribute("isOwner", principal != null && principal.userId().equals(gif.getOwnerId()));
        model.addAttribute("isAdmin", principal != null && principal.admin());
        model.addAttribute("reasons", com.kismetsoftware.politigif.report.Reason.values());
        return "share";
    }

    @GetMapping("/g/{id}.mp4")
    public void mp4(@PathVariable String id, @RequestHeader(value = "Range", required = false) String range,
                    @AuthenticationPrincipal PolitigifPrincipal principal,
                    HttpServletResponse response) throws IOException {
        Gif gif = visible(id, principal);
        long size = storage.size(gif.storageKey());
        response.setContentType("video/mp4");
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Disposition", "inline; filename=\"politigif-" + gif.getId() + ".mp4\"");
        long[] span = ByteRange.parse(range, size);
        if (span == null && range != null && range.startsWith("bytes=") && range.indexOf(',') < 0) {
            response.setStatus(416);
            response.setHeader("Content-Range", "bytes */" + size);
            return;
        }
        if (span == null) {
            response.setContentLengthLong(size);
            try (InputStream in = storage.open(gif.storageKey())) {
                mediaService.passthrough(in, response.getOutputStream());
            }
            return;
        }
        response.setStatus(206);
        response.setHeader("Content-Range", "bytes " + span[0] + "-" + span[1] + "/" + size);
        response.setContentLengthLong(span[1] - span[0] + 1);
        try (InputStream in = storage.openRange(gif.storageKey(), span[0], span[1])) {
            mediaService.passthrough(in, response.getOutputStream());
        }
    }

    @GetMapping("/g/{id}.gif")
    public void gif(@PathVariable String id, @AuthenticationPrincipal PolitigifPrincipal principal,
                    HttpServletResponse response) throws IOException {
        render(visible(id, principal), MediaService.Format.GIF, "gif", response);
    }

    @GetMapping("/g/{id}.webp")
    public void webp(@PathVariable String id, @AuthenticationPrincipal PolitigifPrincipal principal,
                    HttpServletResponse response) throws IOException {
        render(visible(id, principal), MediaService.Format.WEBP, "webp", response);
    }

    @GetMapping("/g/{id}/poster.jpg")
    public void poster(@PathVariable String id, @AuthenticationPrincipal PolitigifPrincipal principal,
                    HttpServletResponse response) throws IOException {
        render(visible(id, principal), MediaService.Format.POSTER, "jpg", response);
    }

    private void render(Gif gif, MediaService.Format format, String extension,
                        HttpServletResponse response) throws IOException {
        response.setContentType(format.contentType());
        response.setHeader("Content-Disposition", "inline; filename=\"politigif-" + gif.getId() + "." + extension + "\"");
        mediaService.render(gif.storageKey(), format, response.getOutputStream());
    }

    /** The owner hides their GIF. A signed-in session is enough; no seedling. Not theirs → 404. */
    @PostMapping("/g/{id}/hide")
    public String hide(@PathVariable String id, @AuthenticationPrincipal PolitigifPrincipal principal,
                       RedirectAttributes redirect) {
        gifService.setHiddenByOwner(id, principal.userId(), true).orElseThrow(NotFoundException::new);
        redirect.addFlashAttribute("notice", "Hidden. Only you can see it now.");
        return "redirect:/g/" + id;
    }

    /** The owner unhides their GIF; one an admin also hid stays hidden. */
    @PostMapping("/g/{id}/unhide")
    public String unhide(@PathVariable String id, @AuthenticationPrincipal PolitigifPrincipal principal,
                         RedirectAttributes redirect) {
        Gif gif = gifService.setHiddenByOwner(id, principal.userId(), false).orElseThrow(NotFoundException::new);
        redirect.addFlashAttribute("notice", gif.isHiddenByAdmin()
                ? "You've unhidden it, but an admin has hidden it, so it stays hidden."
                : "It's public again.");
        return "redirect:/g/" + id;
    }

    /** Hidden GIFs answer 404 to everyone but their owner and admins, exactly like a missing id. */
    private Gif visible(String id, PolitigifPrincipal principal) {
        return gifService.findVisible(id, principal == null ? null : principal.userId(),
                principal != null && principal.admin()).orElseThrow(NotFoundException::new);
    }
}
