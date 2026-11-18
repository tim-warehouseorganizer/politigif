/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every POST form must use {@code th:action}, never a plain {@code action}.
 *
 * <p>Thymeleaf adds the CSRF hidden field only to forms whose action goes through
 * {@code th:action}. A plain {@code action="/x"} renders fine and submits fine — and Spring
 * Security answers it with a 403 that looks like a permissions bug. That is exactly how the
 * production Team page's invite form failed on 2026-10-13: the scripted tests passed because
 * they added the token by hand, so only a real browser found it. This scans the source instead,
 * so a new form can't regress it.
 */
class PostFormsCarryCsrfTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    /** A whole opening tag, across line breaks. */
    private static final Pattern FORM_TAG = Pattern.compile("<form\\b[^>]*>", Pattern.DOTALL);

    @Test
    void everyPostFormGetsItsTokenFromThAction() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".html")).toList()) {
                Matcher tag = FORM_TAG.matcher(Files.readString(file));
                while (tag.find()) {
                    String form = tag.group().toLowerCase(Locale.ROOT);
                    boolean post = form.contains("method=\"post\"");
                    if (post && !form.contains("th:action=")) {
                        offenders.add(TEMPLATES.relativize(file) + ": " + tag.group().replaceAll("\\s+", " "));
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "POST forms without th:action get no CSRF token and every submit is a 403:\n  "
                        + String.join("\n  ", offenders));
    }
}
