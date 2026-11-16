package com.sfaasproject.stf.measurement.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/** Constant-time verifier for the Node-RED X-Internal-Api-Key header. */
public class InternalApiKeyVerifier {
    private final byte[] expected;

    public InternalApiKeyVerifier(String configuredKey) {
        if (configuredKey == null
                || configuredKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Internal API key must be at least 32 bytes");
        }
        this.expected = configuredKey.getBytes(StandardCharsets.UTF_8);
    }

    public boolean matches(String suppliedKey) {
        if (suppliedKey == null) return false;
        byte[] actual = suppliedKey.getBytes(StandardCharsets.UTF_8);
        boolean matches = MessageDigest.isEqual(expected, actual);
        Arrays.fill(actual, (byte) 0);
        return matches;
    }
}
