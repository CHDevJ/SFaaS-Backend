package com.sfaasproject.stf.auth.dto;

import com.sfaasproject.stf.auth.service.AuthFlowService;
import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import com.sfaasproject.stf.common.time.KstTime;
import java.time.OffsetDateTime;

/** AUTH-004 응답 데이터(openapi CurrentUser). 비밀번호와 해시는 담지 않는다. */
public record CurrentUserResponse(
        long userId,
        String loginId,
        String name,
        Position position,
        Permission permission,
        AccountStatus accountStatus,
        OffsetDateTime lastLoginAt) {

    public static CurrentUserResponse from(AuthFlowService.User user) {
        return new CurrentUserResponse(user.userId(), user.loginId(), user.name(), user.position(),
                user.permission(), user.status(), KstTime.toOffset(user.lastLoginAt()));
    }
}
