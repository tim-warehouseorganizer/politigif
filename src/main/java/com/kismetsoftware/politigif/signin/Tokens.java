/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.signin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Hashing and randomness for sign-in links. The link token is a {@link UUID#randomUUID()} (122
 * random bits) and the browser nonce is 32 random bytes; both are high-entropy, so a plain SHA-256
 * is the right hash (BCrypt's slowness defends low-entropy secrets, which these are not). Only the
 * hashes are stored, so a database dump holds no usable link.
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern UUID_SHAPE =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern NONCE_SHAPE = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private Tokens() {
    }

    public static String newLinkToken() {
        return UUID.randomUUID().toString();
    }

    /** 32 random bytes, base64url without padding (43 characters): cookie-safe as-is. */
    public static String newNonce() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean isLinkToken(String token) {
        return token != null && UUID_SHAPE.matcher(token).matches();
    }

    public static boolean isNonce(String nonce) {
        return nonce != null && NONCE_SHAPE.matcher(nonce).matches();
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }

    /** Constant-time comparison of two stored-hash strings; false if either is null. */
    public static boolean hashesEqual(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
