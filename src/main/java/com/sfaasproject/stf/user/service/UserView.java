package com.sfaasproject.stf.user.service;

import com.sfaasproject.stf.auth.service.AuthPolicyService;
import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.user.entity.AccountEntity;
import java.time.Instant;

/** 승인된 계정 한 건을 서비스 밖으로 내보내는 모양. 비밀번호 해시와 텔레그램 대화 번호는 담지 않는다. */
public record UserView(long userId, String loginId, String name, boolean telegramConnected,
        Instant telegramLinkedAt, Position position, Permission permission, AccountStatus accountStatus,
        Instant lastLoginAt, Instant createdAt, Instant updatedAt) {

    /** 승인된(직책이 있는) 계정만 넘겨야 한다. */
    public static UserView from(AccountEntity account, AuthPolicyService policy) {
        Position position = Position.valueOf(account.getRole());
        AccountStatus status = account.isMustChangePassword()
                ? AccountStatus.PASSWORD_CHANGE_REQUIRED : AccountStatus.ACTIVE;
        return new UserView(account.getId(), account.getLoginId(), account.getName(),
                account.isTelegramConnected(), KstTime.toInstant(account.getTelegramLinkedAt()), position,
                policy.permission(position), status, KstTime.toInstant(account.getLastLoginAt()),
                KstTime.toInstant(account.getCreatedAt()), KstTime.toInstant(account.getUpdatedAt()));
    }
}
