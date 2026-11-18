/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tags as typed ("#Senate, debate  #2026") become a clean list: lowercase, {@code [a-z0-9_-]}
 * only, 1-30 characters, no duplicates, at most {@link #MAX_TAGS}, in the order given.
 */
public final class Tags {

    public static final int MAX_TAGS = 10;
    public static final int MAX_LENGTH = 30;

    private Tags() {
    }

    public static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        for (String part : raw.split("[\\s,]+")) {
            String tag = normalize(part);
            if (tag != null) {
                tags.add(tag);
            }
            if (tags.size() == MAX_TAGS) {
                break;
            }
        }
        return new ArrayList<>(tags);
    }

    /** One tag, cleaned; null when nothing usable is left. */
    public static String normalize(String part) {
        if (part == null) {
            return null;
        }
        String tag = part.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (tag.isEmpty()) {
            return null;
        }
        return tag.length() > MAX_LENGTH ? tag.substring(0, MAX_LENGTH) : tag;
    }
}
