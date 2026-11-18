package com.sfaasproject.stf.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.user.service.UserView;
import java.time.OffsetDateTime;

/**
 * 계정 한 건 (openapi User). 비밀번호와 해시는 어떤 경우에도 담지 않는다.
 * 목록(USR-001)은 계약에 따라 최종 로그인 시각을 빼고, 상세에서는 로그인한 적이 없을 때 생략한다.
 */
public record UserResponse(
        long userId,
        String loginId,
        String name,
        boolean telegramConnected,
        OffsetDateTime telegramLinkedAt,
        Permission permission,
        AccountStatus accountStatus,
        @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static UserResponse from(UserView user) {
        return of(user, KstTime.toOffset(user.lastLoginAt()));
    }

    /** USR-001 목록용. 최종 로그인 시각은 담지 않는다. */
    public static UserResponse forList(UserView user) {
        return of(user, null);
    }

    private static UserResponse of(UserView user, OffsetDateTime lastLoginAt) {
        return new UserResponse(user.userId(), user.loginId(), user.name(), user.telegramConnected(),
                KstTime.toOffset(user.telegramLinkedAt()), user.permission(),
                user.accountStatus(), lastLoginAt, KstTime.toOffset(user.createdAt()),
                KstTime.toOffset(user.updatedAt()));
    }
}
