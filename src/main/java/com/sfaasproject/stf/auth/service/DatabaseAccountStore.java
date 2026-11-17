package com.sfaasproject.stf.auth.service;

import com.sfaasproject.stf.auth.service.AuthFlowService.Account;
import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.entity.ApprovalStatus;
import com.sfaasproject.stf.user.repository.AccountRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ERD account 테이블을 읽고 쓰는 {@link AuthFlowService.AccountStore} 구현.
 * 삭제한 계정(deleted_at 이 채워진 줄)은 없는 계정처럼 취급해 로그인과 토큰 사용을 막는다.
 */
@Component
public class DatabaseAccountStore implements AuthFlowService.AccountStore {

    private final AccountRepository accounts;
    private final Clock clock;

    public DatabaseAccountStore(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Account findByLoginId(String loginId) {
        return accounts.findByLoginId(loginId)
                .filter(entity -> !entity.isDeleted())
                .map(DatabaseAccountStore::toAccount)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Account findById(long userId) {
        return accounts.findById(userId)
                .filter(entity -> !entity.isDeleted())
                .map(DatabaseAccountStore::toAccount)
                .orElse(null);
    }

    @Override
    @Transactional
    public void recordSuccessfulLogin(long userId, Instant at) {
        AccountEntity entity = accounts.findById(userId).orElseThrow(
                () -> new IllegalStateException("Account disappeared during login"));
        entity.recordLogin(KstTime.toLocal(at));
    }

    @Override
    @Transactional
    public void changePassword(long userId, String passwordHash) {
        AccountEntity entity = accounts.findById(userId).orElseThrow(
                () -> new IllegalStateException("Account not found"));
        entity.changePassword(passwordHash, KstTime.toLocal(clock.instant()));
    }

    static Account toAccount(AccountEntity entity) {
        return new Account(entity.getId(), entity.getLoginId(), entity.getName(),
                position(entity.getRole()), status(entity), entity.getPasswordHash(),
                KstTime.toInstant(entity.getLastLoginAt()));
    }

    /** 승인 대기 -> 비밀번호 변경 필요 -> 사용 순서로 계정 상태를 정한다. */
    static AccountStatus status(AccountEntity entity) {
        if (entity.getApprovalStatus() == ApprovalStatus.PENDING) {
            return AccountStatus.PENDING_APPROVAL;
        }
        return entity.isMustChangePassword()
                ? AccountStatus.PASSWORD_CHANGE_REQUIRED : AccountStatus.ACTIVE;
    }

    private static Position position(String role) {
        if (role == null) {
            return null;
        }
        try {
            return Position.valueOf(role);
        } catch (IllegalArgumentException ex) {
            return null; // 알 수 없는 직책은 로그인과 토큰 사용을 거부한다.
        }
    }
}
