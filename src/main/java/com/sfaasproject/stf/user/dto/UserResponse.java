package com.sfaasproject.stf.user.dto;

import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.user.service.SignupApprovalService;
import java.time.OffsetDateTime;

/** 계정 한 건 (openapi User). 비밀번호와 해시는 어떤 경우에도 담지 않는다. */
public record UserResponse(
        long userId,
        String loginId,
        String name,
        boolean telegramConnected,
        OffsetDateTime telegramLinkedAt,
        Position position,
        Permission permission,
        AccountStatus accountStatus,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static UserResponse from(SignupApprovalService.ApprovedUser user) {
        return new UserResponse(user.userId(), user.loginId(), user.name(), user.telegramConnected(),
                KstTime.toOffset(user.telegramLinkedAt()), user.position(), user.permission(),
                user.accountStatus(), KstTime.toOffset(user.lastLoginAt()),
                KstTime.toOffset(user.createdAt()), KstTime.toOffset(user.updatedAt()));
    }
}
