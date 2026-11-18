package com.sfaasproject.stf.auth.dto;

import com.sfaasproject.stf.auth.service.AuthFlowService;
import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.common.time.KstTime;
import java.time.OffsetDateTime;

/**
 * AUTH-001 응답 데이터(openapi LoginData). Refresh Token 은 본문이 아니라 HttpOnly 쿠키로만 내려간다.
 * 토큰이 로그에 남지 않도록 {@link #toString()}에서 가린다.
 */
public record LoginResponse(
        long userId,
        String loginId,
        String name,
        Permission permission,
        AccountStatus accountStatus,
        OffsetDateTime lastLoginAt,
        String accessToken,
        String tokenType,
        OffsetDateTime expiresAt) {

    public static final String BEARER = "Bearer";

    public static LoginResponse from(AuthFlowService.Login login) {
        AuthFlowService.User user = login.user();
        return new LoginResponse(user.userId(), user.loginId(), user.name(),
                user.permission(), user.status(), KstTime.toOffset(user.lastLoginAt()),
                login.accessToken(), BEARER, KstTime.toOffset(login.accessExpiresAt()));
    }

    @Override
    public String toString() {
        return "LoginResponse[userId=" + userId + ", loginId=" + loginId + ", accessToken=****]";
    }
}
