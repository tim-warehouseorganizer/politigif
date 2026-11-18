/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Switch and overrides for the limiter. Capacities live in {@link RateLimitPolicy} as defaults
 * and are overridable by key, because the right number is discovered in production and finding it
 * should not need a deploy:
 *
 * <pre>
 * app.rate-limit.enabled=true
 * app.rate-limit.capacities.token:email=20
 * </pre>
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    private final Map<String, Integer> capacities = new HashMap<>();

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Integer> getCapacities() {
        return capacities;
    }

    public int capacityFor(RateLimitPolicy policy) {
        Integer override = capacities.get(policy.key());
        return override == null || override < 1 ? policy.defaultCapacity() : override;
    }
}
