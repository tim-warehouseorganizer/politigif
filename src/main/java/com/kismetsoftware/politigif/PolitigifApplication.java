/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * politigif.com: drop a political GIF, share it with friends.
 *
 * <p>Sign-in is a one-time emailed link bound to the browser that asked for it; there are no
 * passwords. The account secret is a "seedling", asked only for account settings changes.
 * Uploads are GIF only, trimmed to 7.77 s and stored as MP4; every other format is converted
 * fresh on each request. Mongo (metadata), Redis (sessions, rate limits), S3-compatible storage
 * (the MP4s) and ffmpeg (conversions). Deliberately small: no caching, no CDN.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PolitigifApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolitigifApplication.class, args);
    }
}
