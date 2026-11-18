/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import com.kismetsoftware.politigif.media.MediaService;
import com.kismetsoftware.politigif.media.UploadRejectedException;
import com.kismetsoftware.politigif.ratelimit.RateLimitedException;
import com.kismetsoftware.politigif.security.PolitigifPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.Map;

/**
 * {@code /upload}. The page's script sends the file as the raw request body
 * ({@code Content-Type: image/gif}) with the title and tags in the query string, so the body
 * reaches the fusio pipeline as a stream: no multipart parsing, no spool file.
 */
@Controller
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final GifService gifService;
    private final MediaService mediaService;

    public UploadController(GifService gifService, MediaService mediaService) {
        this.gifService = gifService;
        this.mediaService = mediaService;
    }

    @GetMapping("/upload")
    public String form(Model model) {
        model.addAttribute("maxBytes", mediaService.maxUploadBytes());
        return "upload";
    }

    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> upload(@AuthenticationPrincipal PolitigifPrincipal principal,
                                                      @RequestParam(defaultValue = "") String title,
                                                      @RequestParam(defaultValue = "") String tags,
                                                      HttpServletRequest request) throws IOException {
        // A declared length over the cap is refused before any byte is read.
        if (request.getContentLengthLong() > mediaService.maxUploadBytes()) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, "That GIF is over 20 MB. Try a smaller one.");
        }
        try {
            Gif gif = gifService.upload(principal.userId(), title, tags, request.getInputStream());
            return ResponseEntity.ok(Map.of("id", gif.getId(), "url", "/g/" + gif.getId(), "trimmed", gif.isTrimmed()));
        } catch (UploadRejectedException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (RateLimitedException e) {
            return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        } catch (IOException e) {
            log.warn("upload failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "That GIF couldn't be converted. Try another one.");
        }
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
