/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.status;

import com.kismetsoftware.politigif.storage.MediaStorage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The no-delete rule as a build check: NO data is ever deleted from the database and no stored MP4
 * is ever deleted (status.Status). Fails if any production source calls or declares a delete /
 * remove / drop, uses a Mongo TTL index, or deletes a storage object or file. Court-ordered
 * deletion is done by hand from docs/court-ordered-deletion.md, never from app code.
 *
 * <p>Comments and string literals are stripped first, so prose about the rule doesn't trip it.
 */
class NoDeleteArchitectureTest {

    private static final Path MAIN = Path.of("src/main/java");

    /** Anything that removes data: Spring Data / MongoTemplate / driver / S3 / java.nio. */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(delete\\w*|remove\\w*|findAndRemove|findAllAndRemove|drop\\w*|deleteObject\\w*)\\s*\\("
                    + "|\\bexpireAfter\\w*\\s*="
                    + "|\\bDelete\\w*Request\\b"
                    + "|\\bFiles\\s*\\.\\s*delete");

    @Test
    void noProductionCodeDeletesData() throws IOException {
        List<String> offences = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = stripCommentsAndStrings(Files.readString(file));
                String[] lines = code.split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    Matcher m = FORBIDDEN.matcher(lines[i]);
                    if (m.find()) {
                        offences.add(MAIN.relativize(file) + ":" + (i + 1) + "  " + lines[i].strip());
                    }
                }
            }
        }
        assertThat(offences)
                .as("No-delete rule: flip a status flag instead (status.Status). Court orders: docs/court-ordered-deletion.md")
                .isEmpty();
    }

    @Test
    void theStorageInterfaceHasNoWayToDelete() {
        for (Method method : MediaStorage.class.getMethods()) {
            assertThat(method.getName().toLowerCase()).doesNotContain("delete").doesNotContain("remove");
        }
    }

    @Test
    void theCheckItselfCatchesTheUsualSuspects() {
        // Guards the guard: if the pattern rots, this fails instead of silently passing everything.
        for (String bad : List.of("gifs.delete(gif);", "repo.deleteByGifId(id);", "mongo.remove(q, Gif.class);",
                "mongo.findAndRemove(q, X.class);", "storage.delete(key);", "s3.deleteObject(r);",
                "DeleteObjectRequest.builder()", "Files.deleteIfExists(p);", "@Indexed(expireAfter = \"1d\")",
                "mongo.dropCollection(Gif.class);", "void deleteByEmail(String e);")) {
            assertThat(FORBIDDEN.matcher(stripCommentsAndStrings(bad)).find()).as(bad).isTrue();
        }
        for (String fine : List.of("// gifs are never deleted", "log.info(\"deleted nothing\");",
                "gif.retireByReset();", "status = DELETED;", "session.invalidate();")) {
            assertThat(FORBIDDEN.matcher(stripCommentsAndStrings(fine)).find()).as(fine).isFalse();
        }
    }

    /** Blanks comments and string/char literal contents, keeping line breaks for line numbers. */
    static String stripCommentsAndStrings(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                while (i < src.length() && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < src.length() && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
            } else if (c == '"' || c == '\'') {
                char quote = c;
                out.append(quote);
                i++;
                // Text blocks and the expireAfter = "..." case: keep the quote marks, drop contents.
                while (i < src.length() && src.charAt(i) != quote) {
                    if (src.charAt(i) == '\\') {
                        i++;
                    } else if (src.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                out.append(quote);
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
