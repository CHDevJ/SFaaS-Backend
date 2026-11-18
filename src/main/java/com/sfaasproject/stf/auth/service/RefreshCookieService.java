package com.sfaasproject.stf.auth.service;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/** Browser refresh cookie policy from OpenAPI v6. */
@Service
public class RefreshCookieService {
    public static final String NAME = "stfRefreshToken";
    private static final String PATH = "/api/v1/auth";

    public ResponseCookie issue(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }
        return base(token).maxAge(RefreshTokenService.LIFETIME).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(true)
                .sameSite("Strict").path(PATH);
    }
}
