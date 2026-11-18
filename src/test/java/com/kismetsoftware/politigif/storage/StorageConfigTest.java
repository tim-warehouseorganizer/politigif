/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageConfigTest {

    // ---- SpacesStorage.objectKey: every object under the app's prefix ----

    @Test
    void keysGoUnderThePrefix() {
        assertThat(SpacesStorage.objectKey("politigif/", "gifs/a.mp4")).isEqualTo("politigif/gifs/a.mp4");
    }

    @Test
    void aPrefixWithoutATrailingSlashGetsOne() {
        assertThat(SpacesStorage.objectKey("politigif", "gifs/a.mp4")).isEqualTo("politigif/gifs/a.mp4");
    }

    @Test
    void aBlankPrefixLeavesTheKeyAlone() {
        assertThat(SpacesStorage.objectKey("", "gifs/a.mp4")).isEqualTo("gifs/a.mp4");
        assertThat(SpacesStorage.objectKey(null, "gifs/a.mp4")).isEqualTo("gifs/a.mp4");
    }

    // ---- StorageConfig.missing: named by setting, never by value ----

    @Test
    void nothingIsMissingWhenBucketKeyAndSecretAreSet() {
        assertThat(StorageConfig.missing("b", "k", "s")).isEmpty();
    }

    @Test
    void eachBlankSettingIsNamedWithBothVariableNames() {
        assertThat(StorageConfig.missing("", " ", null)).containsExactly(
                "bucket (SPACES_BUCKET / BUCKETEER_BUCKET_NAME)",
                "access key (SPACES_KEY / BUCKETEER_AWS_ACCESS_KEY_ID)",
                "secret (SPACES_SECRET / BUCKETEER_AWS_SECRET_ACCESS_KEY)");
    }

    @Test
    void theEndpointIsOptionalForAwsAndTheBootFailsOnlyOnTheRealGaps() {
        StorageConfig config = new StorageConfig();

        assertThatThrownBy(() -> config.mediaStorage("spaces", "", "", "us-east-1", "bucket", "", "secret-value", "politigif/"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access key")
                .hasMessageNotContaining("secret-value")
                .hasMessageNotContaining("endpoint");
    }
}
