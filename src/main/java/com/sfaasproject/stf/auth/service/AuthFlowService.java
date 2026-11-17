package com.sfaasproject.stf.auth.service;

import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.common.DomainException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * AUTH-001..004 application rules. Account and session ports must be implemented
 * with transactional persistence and atomic refresh rotation before HTTP wiring.
 */
public class AuthFlowService {
    public record Account(long userId, String loginId, String name, Permission permission,
            AccountStatus status, String passwordHash, Instant lastLoginAt) { }
    public record User(long userId, String loginId, String name, Permission permission, AccountStatus status, Instant lastLoginAt) { }
    public record Login(User user, String accessToken, Instant accessExpiresAt,
            String refreshToken, Instant refreshExpiresAt) {
        @Override public String toString() { return "Login[redacted]"; }
    }
    public record Refreshed(String accessToken, Instant accessExpiresAt,
            String refreshToken, Instant refreshExpiresAt) {
        @Override public String toString() { return "Refreshed[redacted]"; }
    }

    public interface AccountStore {
        Account findByLoginId(String loginId);
        Account findById(long userId);
        void recordSuccessfulLogin(long userId, Instant at);
        void changePassword(long userId, String passwordHash);
    }

    /**
     * Rotation must atomically mark an old hash used and store the replacement.
     * A reused hash must return REUSED with its family so the whole family can be revoked.
     * revokeAllForUser must invalidate outstanding access JWTs as well as refresh families.
     */
    public interface SessionStore {
        enum RotationStatus { ROTATED, INVALID, REUSED }
        record Rotation(RotationStatus status, Long userId, String familyId) { }
        void create(long userId, String familyId, String refreshHash, Instant expiresAt);
        Rotation rotate(String oldHash, String nextHash, Instant nextExpiresAt, Instant now);
        void revokeFamily(String familyId);
        void revokeFamilyByRefreshHash(String refreshHash);
        void revokeAccess(String jti, Instant expiresAt);
        void revokeAllForUser(long userId, Instant at);
        boolean isAccessRevoked(String jti, long userId, Instant issuedAt);
    }

    private final AccountStore accounts;
    private final SessionStore sessions;
    private final AuthPolicyService policy;
    private final JwtAccessTokenService access;
    private final RefreshTokenService refresh;
    private final PasswordEncoder passwords;

    public AuthFlowService(AccountStore accounts, SessionStore sessions,
            AuthPolicyService policy, JwtAccessTokenService access,
            RefreshTokenService refresh, PasswordEncoder passwords) {
        this.accounts = Objects.requireNonNull(accounts);
        this.sessions = Objects.requireNonNull(sessions);
        this.policy = Objects.requireNonNull(policy);
        this.access = Objects.requireNonNull(access);
        this.refresh = Objects.requireNonNull(refresh);
        this.passwords = Objects.requireNonNull(passwords);
    }

    public Login login(String loginId, String password, Instant now) {
        Objects.requireNonNull(now);
        try {
            policy.validateLoginId(loginId);
            policy.validatePassword(password);
        } catch (IllegalArgumentException ex) {
            throw error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.");
        }
        Account account = accounts.findByLoginId(loginId);
        if (account == null || account.passwordHash() == null
                || !passwords.matches(password, account.passwordHash())) {
            throw invalidCredentials();
        }
        if (account.status() == AccountStatus.PENDING_APPROVAL) {
            throw error(HttpStatus.FORBIDDEN, "AUTH_APPROVAL_PENDING", "가입 승인 대기 중입니다.");
        }
        if (account.status() == null || account.permission() == null) throw invalidCredentials();

        var accessToken = access.issue(account.userId(), account.permission(), now);
        var refreshToken = refresh.issue(now);
        String familyId = UUID.randomUUID().toString();
        sessions.create(account.userId(), familyId, refreshToken.hash(),
                refreshToken.expiresAt());
        try {
            accounts.recordSuccessfulLogin(account.userId(), now);
        } catch (RuntimeException ex) {
            sessions.revokeFamily(familyId);
            throw ex;
        }
        return new Login(user(account, now), accessToken.value(), accessToken.expiresAt(),
                refreshToken.value(), refreshToken.expiresAt());
    }

    public Refreshed refresh(String rawToken, Instant now) {
        Objects.requireNonNull(now);
        String oldHash;
        try {
            oldHash = refresh.hash(rawToken);
        } catch (IllegalArgumentException ex) {
            throw invalidRefresh();
        }
        var next = refresh.issue(now);
        SessionStore.Rotation rotation = sessions.rotate(oldHash, next.hash(),
                next.expiresAt(), now);
        if (rotation == null || rotation.status() == SessionStore.RotationStatus.INVALID) {
            throw invalidRefresh();
        }
        if (rotation.status() == SessionStore.RotationStatus.REUSED) {
            if (rotation.familyId() != null) sessions.revokeFamily(rotation.familyId());
            throw invalidRefresh();
        }
        if (rotation.userId() == null || rotation.familyId() == null) throw invalidRefresh();
        Account account = accounts.findById(rotation.userId());
        if (account == null || account.status() == AccountStatus.PENDING_APPROVAL
                || account.status() == null || account.permission() == null) {
            sessions.revokeFamily(rotation.familyId());
            throw invalidRefresh();
        }
        var accessToken = access.issue(account.userId(), account.permission(), now);
        return new Refreshed(accessToken.value(), accessToken.expiresAt(),
                next.value(), next.expiresAt());
    }

    public User currentUser(String accessToken, Instant now) {
        var claims = access.verify(accessToken, now);
        if (sessions.isAccessRevoked(claims.jti(), claims.userId(), claims.issuedAt())) {
            throw invalidAccess();
        }
        Account account = accounts.findById(claims.userId());
        if (account == null || account.status() == AccountStatus.PENDING_APPROVAL
                || account.status() == null || account.permission() == null) {
            throw invalidAccess();
        }
        return user(account, account.lastLoginAt());
    }

    public void logout(String accessToken, String rawRefreshToken, Instant now) {
        var claims = access.verify(accessToken, now);
        sessions.revokeAccess(claims.jti(), claims.expiresAt());
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            sessions.revokeFamilyByRefreshHash(refresh.hash(rawRefreshToken));
        }
    }

    public void changePassword(String accessToken, String currentPassword,
            String newPassword, Instant now) {
        var claims = access.verify(accessToken, now);
        if (sessions.isAccessRevoked(claims.jti(), claims.userId(), claims.issuedAt())) {
            throw invalidAccess();
        }
        Account account = accounts.findById(claims.userId());
        if (account == null || account.status() == AccountStatus.PENDING_APPROVAL
                || account.status() == null || account.passwordHash() == null) {
            throw invalidAccess();
        }
        if (currentPassword == null
                || !passwords.matches(currentPassword, account.passwordHash())) {
            throw invalidCredentials();
        }
        try {
            policy.validatePassword(newPassword);
        } catch (IllegalArgumentException ex) {
            throw error(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_VIOLATION",
                    "비밀번호 형식을 확인해 주세요.");
        }
        // Fail closed if token revocation is unavailable.
        sessions.revokeAllForUser(account.userId(), now);
        accounts.changePassword(account.userId(), passwords.encode(newPassword));
    }
    private User user(Account account, Instant lastLoginAt) {
        return new User(account.userId(), account.loginId(), account.name(), account.permission(), account.status(), lastLoginAt);
    }

    private DomainException invalidCredentials() {
        return error(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS",
                "아이디 또는 비밀번호를 확인해 주세요.");
    }

    private DomainException invalidRefresh() {
        return error(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_INVALID",
                "다시 로그인해 주세요.");
    }

    private DomainException invalidAccess() {
        return error(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "다시 로그인해 주세요.");
    }

    private DomainException error(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}
