package com.sfaasproject.stf.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sfaasproject.stf.auth.service.AuthFlowService.Account;
import com.sfaasproject.stf.auth.service.AuthFlowService.AccountStore;
import com.sfaasproject.stf.auth.service.AuthFlowService.SessionStore;
import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.common.DomainException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthFlowServiceTest {
    private final AccountStore accounts = mock(AccountStore.class);
    private final SessionStore sessions = mock(SessionStore.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private final JwtAccessTokenService access = new JwtAccessTokenService(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "stf");
    private final AuthFlowService service = new AuthFlowService(accounts, sessions,
            new AuthPolicyService(), access, new RefreshTokenService(), passwords);
    private final Instant now = Instant.parse("2026-11-17T00:00:00Z");
    private Account active;

    @BeforeEach
    void accountExists() {
        active = new Account(12, "parkjh", "박지훈", Permission.USER,
                AccountStatus.ACTIVE, passwords.encode("Password1!"), null);
        when(accounts.findByLoginId("parkjh")).thenReturn(active);
        when(accounts.findById(12)).thenReturn(active);
    }

    @Test
    void successfulLoginStoresLastLoginAndRefreshFamily() {
        var result = service.login("parkjh", "Password1!", now);
        assertEquals(12, result.user().userId());
        assertEquals(Permission.USER, result.user().permission());
        assertEquals(now, result.user().lastLoginAt());
        assertEquals(now.plusSeconds(1800), result.accessExpiresAt());
        assertEquals(now.plusSeconds(604800), result.refreshExpiresAt());
        assertEquals("Login[redacted]", result.toString());
        verify(accounts).recordSuccessfulLogin(12, now);
        verify(sessions).create(eq(12L), anyString(), anyString(),
                eq(now.plusSeconds(604800)));
    }

    @Test
    void badCredentialsDoNotUpdateLastLogin() {
        var error = assertThrows(DomainException.class,
                () -> service.login("parkjh", "WrongPass1!", now));
        assertEquals("AUTH_INVALID_CREDENTIALS", error.code());
        verify(accounts, never()).recordSuccessfulLogin(anyLong(), any());
        verifyNoInteractions(sessions);
    }

    @Test
    void pendingAccountIsForbiddenAfterPasswordCheck() {
        when(accounts.findByLoginId("parkjh")).thenReturn(new Account(12, "parkjh",
                "박지훈", Permission.USER, AccountStatus.PENDING_APPROVAL,
                active.passwordHash(), null));
        var error = assertThrows(DomainException.class,
                () -> service.login("parkjh", "Password1!", now));
        assertEquals("AUTH_APPROVAL_PENDING", error.code());
        verify(accounts, never()).recordSuccessfulLogin(anyLong(), any());
    }

    @Test
    void refreshRotatesButDoesNotChangeLastLogin() {
        when(sessions.rotate(anyString(), anyString(), any(), eq(now)))
                .thenReturn(new SessionStore.Rotation(
                        SessionStore.RotationStatus.ROTATED, 12L, "family"));
        var result = service.refresh("old-refresh", now);
        assertEquals(now.plusSeconds(1800), result.accessExpiresAt());
        assertNotEquals("old-refresh", result.refreshToken());
        assertEquals("Refreshed[redacted]", result.toString());
        verify(accounts, never()).recordSuccessfulLogin(anyLong(), any());
    }

    @Test
    void replayRevokesWholeFamilyAndRejectsRefresh() {
        when(sessions.rotate(anyString(), anyString(), any(), eq(now)))
                .thenReturn(new SessionStore.Rotation(
                        SessionStore.RotationStatus.REUSED, 12L, "family"));
        var error = assertThrows(DomainException.class,
                () -> service.refresh("replayed-token", now));
        assertEquals("AUTH_REFRESH_TOKEN_INVALID", error.code());
        verify(sessions).revokeFamily("family");
    }

    @Test
    void currentUserRequeriesAccountAndLogoutRevokesAccessAndFamily() {
        var jwt = access.issue(12, Permission.USER, now);
        assertEquals(12, service.currentUser(jwt.value(), now).userId());
        service.logout(jwt.value(), "refresh-value", now);
        verify(sessions).revokeAccess(jwt.jti(), jwt.expiresAt());
        verify(sessions).revokeFamilyByRefreshHash(anyString());
        when(sessions.isAccessRevoked(eq(jwt.jti()), eq(12L), any())).thenReturn(true);
        assertEquals("AUTH_TOKEN_INVALID", assertThrows(DomainException.class,
                () -> service.currentUser(jwt.value(), now)).code());
    }
    @Test
    void passwordChangeValidatesCurrentAndNewThenRevokesEveryToken() {
        var jwt = access.issue(12, Permission.USER, now);
        service.changePassword(jwt.value(), "Password1!", "NewPassword2!", now);
        verify(accounts).changePassword(eq(12L), argThat(hash ->
                passwords.matches("NewPassword2!", hash)));
        verify(sessions).revokeAllForUser(12, now);
        verify(accounts, never()).recordSuccessfulLogin(anyLong(), any());

        assertEquals("AUTH_INVALID_CREDENTIALS", assertThrows(DomainException.class,
                () -> service.changePassword(jwt.value(), "WrongPass1!",
                        "NewPassword2!", now)).code());
        assertEquals("PASSWORD_POLICY_VIOLATION", assertThrows(DomainException.class,
                () -> service.changePassword(jwt.value(), "Password1!",
                        "weak", now)).code());
        verify(accounts, times(1)).changePassword(anyLong(), anyString());
    }
    @Test
    void failedLastLoginWriteRevokesNewRefreshFamily() {
        doThrow(new IllegalStateException("DB unavailable"))
                .when(accounts).recordSuccessfulLogin(12, now);
        assertThrows(IllegalStateException.class,
                () -> service.login("parkjh", "Password1!", now));
        verify(sessions).revokeFamily(anyString());
    }

    @Test
    void failedTokenRevocationPreventsPasswordUpdate() {
        var jwt = access.issue(12, Permission.USER, now);
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(sessions).revokeAllForUser(12, now);
        assertThrows(IllegalStateException.class,
                () -> service.changePassword(jwt.value(), "Password1!",
                        "NewPassword2!", now));
        verify(accounts, never()).changePassword(anyLong(), anyString());
    }
}
