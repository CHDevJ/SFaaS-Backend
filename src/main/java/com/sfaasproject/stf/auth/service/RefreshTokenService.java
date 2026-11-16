package com.sfaasproject.stf.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** Opaque 256-bit refresh token material; only its SHA-256 hash is persisted. */
public class RefreshTokenService {
    public static final Duration LIFETIME = Duration.ofDays(7);
    private final SecureRandom random = new SecureRandom();

    public record Issued(String value, String hash, Instant expiresAt) {
        @Override public String toString() { return "Issued[redacted]"; }
    }

    public Issued issue(Instant now) {
        if (now == null) throw new IllegalArgumentException("Current time is required");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Issued(value, hash(value), now.plus(LIFETIME));
    }

    public String hash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
