/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

/**
 * One HTTP byte range ({@code bytes=a-b}, {@code bytes=a-}, {@code bytes=-n}) against an object of
 * {@code size} bytes. Multi-range requests are answered with the whole object (null), which RFC
 * 9110 allows; browsers' video players only ever ask for one.
 */
public final class ByteRange {

    private ByteRange() {
    }

    /**
     * @return {@code {first, last}} inclusive, or null for "send it all" (no header, a multi-range,
     *         or a header that isn't a byte range). An unsatisfiable single range also returns null;
     *         the caller tells those apart by the {@code bytes=} prefix and answers 416.
     */
    public static long[] parse(String header, long size) {
        if (header == null || !header.startsWith("bytes=") || header.indexOf(',') >= 0 || size <= 0) {
            return null;
        }
        String spec = header.substring(6).strip();
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        try {
            String a = spec.substring(0, dash).strip();
            String b = spec.substring(dash + 1).strip();
            long first;
            long last;
            if (a.isEmpty()) {
                long suffix = Long.parseLong(b);
                if (suffix <= 0) {
                    return null;
                }
                first = Math.max(0, size - suffix);
                last = size - 1;
            } else {
                first = Long.parseLong(a);
                last = b.isEmpty() ? size - 1 : Math.min(Long.parseLong(b), size - 1);
            }
            if (first < 0 || first >= size || last < first) {
                return null;
            }
            return new long[]{first, last};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
