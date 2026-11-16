package com.sfaasproject.stf.measurement.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InternalApiKeyVerifierTest {
    private final String secret = "0123456789abcdef0123456789abcdef";

    @Test
    void acceptsOnlyExactConfiguredSecret() {
        var verifier = new InternalApiKeyVerifier(secret);
        assertTrue(verifier.matches(secret));
        assertFalse(verifier.matches(secret + "x"));
        assertFalse(verifier.matches(null));
        assertFalse(verifier.matches("0123456789abcdef0123456789abcdeg"));
    }

    @Test
    void rejectsWeakConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new InternalApiKeyVerifier("weak"));
    }
}
