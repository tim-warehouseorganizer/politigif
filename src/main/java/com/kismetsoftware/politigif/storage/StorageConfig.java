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

/** {@code app.storage.type}: {@code spaces} (default, production) or {@code local} (dev profile). */
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
                                     @Value("${app.storage.spaces.secret:}") String secret) throws IOException {
        if ("local".equalsIgnoreCase(type)) {
            log.info("media storage: local folder {}", Path.of(localDir).toAbsolutePath());
            return new LocalFolderStorage(Path.of(localDir));
        }
        if (endpoint.isBlank() || bucket.isBlank() || key.isBlank() || secret.isBlank()) {
            // Fail the boot: an app that accepts uploads it cannot store is worse than one that is down.
            throw new IllegalStateException("app.storage.type=spaces needs SPACES_ENDPOINT, SPACES_BUCKET, "
                    + "SPACES_KEY and SPACES_SECRET (or run with the dev profile for local storage)");
        }
        log.info("media storage: Spaces bucket {} at {}", bucket, endpoint);
        return new SpacesStorage(endpoint, region, bucket, key, secret);
    }
}
