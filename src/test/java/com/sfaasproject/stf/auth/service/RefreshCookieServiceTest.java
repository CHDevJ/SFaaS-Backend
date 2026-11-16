package com.sfaasproject.stf.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RefreshCookieServiceTest {
    private final RefreshCookieService service = new RefreshCookieService();

    @Test
    void browserCookieIsRestrictedAndClearedAtLogout() {
        var issued = service.issue("opaque-token");
        assertTrue(issued.isHttpOnly());
        assertTrue(issued.isSecure());
        assertEquals("Strict", issued.getSameSite());
        assertEquals("/api/v1/auth", issued.getPath());
        assertEquals(RefreshTokenService.LIFETIME, issued.getMaxAge());
        assertEquals("opaque-token", issued.getValue());

        var cleared = service.clear();
        assertEquals("", cleared.getValue());
        assertEquals(java.time.Duration.ZERO, cleared.getMaxAge());
        assertEquals(issued.getPath(), cleared.getPath());
        assertTrue(cleared.isHttpOnly());
    }
}
