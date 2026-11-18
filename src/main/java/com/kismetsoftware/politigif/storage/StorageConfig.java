/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code app.storage.type}: {@code spaces} (default, production: any S3-compatible bucket) or
 * {@code local} (dev profile). The S3 settings come from {@code SPACES_*} or, when those are unset,
 * a Heroku Bucketeer attachment's {@code BUCKETEER_*} vars (see application.properties); with no
 * endpoint the AWS endpoint for the region is used.
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public MediaStorage mediaStorage(@Value("${app.storage.type:spaces}") String type,
                                     @Value("${app.storage.local-dir:./data/media}") String localDir,
                                     @Value("${app.storage.spaces.endpoint:}") String endpoint,
                                     @Value("${app.storage.spaces.region:us-east-1}") String region,
                                     @Value("${app.storage.spaces.bucket:}") String bucket,
                                     @Value("${app.storage.spaces.key:}") String key,
                                     @Value("${app.storage.spaces.secret:}") String secret,
                                     @Value("${app.storage.key-prefix:politigif/}") String keyPrefix) throws IOException {
        if ("local".equalsIgnoreCase(type)) {
            log.info("media storage: local folder {}", Path.of(localDir).toAbsolutePath());
            return new LocalFolderStorage(Path.of(localDir));
        }
        List<String> missing = missing(bucket, key, secret);
        if (!missing.isEmpty()) {
            // Fail the boot: an app that accepts uploads it cannot store is worse than one that is down.
            throw new IllegalStateException("app.storage.type=spaces is missing " + String.join(", ", missing)
                    + " (set SPACES_* or attach Bucketeer; or run with the dev profile for local storage)");
        }
        log.info("media storage: S3 bucket {} at {} under prefix '{}'", bucket,
                endpoint.isBlank() ? "AWS " + region : endpoint, keyPrefix);
        return new SpacesStorage(endpoint, region, bucket, key, secret, keyPrefix);
    }

    /** Which required S3 settings are blank, by name, never by value. */
    static List<String> missing(String bucket, String key, String secret) {
        List<String> missing = new ArrayList<>();
        if (bucket == null || bucket.isBlank()) {
            missing.add("bucket (SPACES_BUCKET / BUCKETEER_BUCKET_NAME)");
        }
        if (key == null || key.isBlank()) {
            missing.add("access key (SPACES_KEY / BUCKETEER_AWS_ACCESS_KEY_ID)");
        }
        if (secret == null || secret.isBlank()) {
            missing.add("secret (SPACES_SECRET / BUCKETEER_AWS_SECRET_ACCESS_KEY)");
        }
        return missing;
    }
}
