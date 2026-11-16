package com.sfaasproject.stf.telegram.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/** 256-bit, ten-minute, one-use token material; Redis consumption is a separate adapter. */
@Service
public class TelegramLinkTokenService {
    public record Issued(String rawToken, String sha256Hash, Instant expiresAt) { }
    private final SecureRandom random = new SecureRandom();

    public Issued issue(Instant now) {
        if (now == null) throw new IllegalArgumentException("Issue time is required");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Issued(raw, hashToken(raw), now.plus(Duration.ofMinutes(10)));
    }

    public boolean matches(String rawToken, String storedHash) {
        if (rawToken == null || storedHash == null) return false;
        return MessageDigest.isEqual(hashToken(rawToken).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                storedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    public String hashToken(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
