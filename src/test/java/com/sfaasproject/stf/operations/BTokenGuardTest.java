package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sfaasproject.stf.auth.service.AuthFlowService.SessionStore;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService.Claims;
import com.sfaasproject.stf.operations.service.BTokenGuard;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BTokenGuardTest {
    final SessionStore sessions = mock(SessionStore.class);
    final Claims claims = new Claims(1, Permission.USER, "jti",
            Instant.parse("2026-11-17T00:00:00Z"), Instant.parse("2026-11-17T00:30:00Z"));
    @Test void delegatesRevocationToASessionStore() {
        when(sessions.isAccessRevoked("jti",1,claims.issuedAt())).thenReturn(true);
        assertTrue(new BTokenGuard(sessions).revoked(claims));
        verify(sessions).isAccessRevoked("jti",1,claims.issuedAt());
    }
    @Test void missingSessionStoreCannotEnableFallback() {
        assertThrows(NullPointerException.class, () -> new BTokenGuard(null));
    }
    @Test void revocationStoreFailureDoesNotAcceptToken() {
        when(sessions.isAccessRevoked("jti",1,claims.issuedAt())).thenThrow(new IllegalStateException());
        assertThrows(IllegalStateException.class, () -> new BTokenGuard(sessions).revoked(claims));
    }
}
