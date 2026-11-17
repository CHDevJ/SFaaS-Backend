package com.sfaasproject.stf.auth.dto;

import com.sfaasproject.stf.auth.service.AuthFlowService;
import com.sfaasproject.stf.common.time.KstTime;
import java.time.OffsetDateTime;

/**
 * AUTH-002 응답 데이터(openapi TokenData). 새 Refresh Token 은 본문이 아니라 HttpOnly 쿠키로만 내려간다.
 * 토큰이 로그에 남지 않도록 {@link #toString()}에서 가린다.
 */
public record TokenResponse(String accessToken, String tokenType, OffsetDateTime expiresAt) {

    public static TokenResponse from(AuthFlowService.Refreshed refreshed) {
        return new TokenResponse(refreshed.accessToken(), LoginResponse.BEARER,
                KstTime.toOffset(refreshed.accessExpiresAt()));
    }

    @Override
    public String toString() {
        return "TokenResponse[accessToken=****, expiresAt=" + expiresAt + "]";
    }
}
