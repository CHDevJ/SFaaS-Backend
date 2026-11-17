package com.sfaasproject.stf.operations.service;

import com.sfaasproject.stf.auth.service.AuthFlowService.SessionStore;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService.Claims;
import java.util.Objects;

/** HTTP and STOMP share A's revocation policy; there is no second Redis key contract. */
public class BTokenGuard {
    private final SessionStore sessions;

    public BTokenGuard(SessionStore sessions) {
        this.sessions = Objects.requireNonNull(sessions);
    }

    public boolean revoked(Claims claims) {
        return sessions.isAccessRevoked(claims.jti(), claims.userId(), claims.issuedAt());
    }
}
