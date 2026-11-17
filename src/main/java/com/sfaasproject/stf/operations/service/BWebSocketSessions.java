package com.sfaasproject.stf.operations.service;

import com.sfaasproject.stf.auth.service.JwtAccessTokenService.Claims;
import com.sfaasproject.stf.operations.port.BPorts.Users;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/** Rechecks authenticated subscriptions before delivery and while idle. Never retains raw JWTs. */
@Component
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
public class BWebSocketSessions {
    private static final class Client {
        final WebSocketSession socket;
        volatile Claims claims;
        Client(WebSocketSession socket) { this.socket = socket; }
    }

    private final ConcurrentMap<String, Client> clients = new ConcurrentHashMap<>();
    private final Users users;
    private final BTokenGuard tokens;
    private final Clock clock;

    public BWebSocketSessions(Users users, BTokenGuard tokens, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.clock = clock;
    }

    public void opened(WebSocketSession socket) {
        clients.put(socket.getId(), new Client(socket));
    }

    public void authenticated(String sessionId, Claims claims) {
        if (sessionId == null) return;
        clients.computeIfPresent(sessionId, (id, client) -> {
            client.claims = claims;
            return client;
        });
    }

    public void closed(String sessionId) {
        clients.remove(sessionId);
    }

    public boolean permitDelivery(String sessionId) {
        Client client = sessionId == null ? null : clients.get(sessionId);
        if (client == null) return false;
        if (valid(client)) return true;
        deny(sessionId, client);
        return false;
    }

    @Scheduled(fixedDelayString = "${stf.websocket.session-check-interval-ms:10000}")
    public void checkSessions() {
        clients.forEach((id, client) -> {
            if (client.claims != null && !valid(client)) deny(id, client);
        });
    }

    private boolean valid(Client client) {
        try {
            Claims claims = client.claims;
            return client.socket.isOpen() && claims != null
                    && claims.expiresAt().isAfter(clock.instant())
                    && users.active(claims.userId()) != null && !tokens.revoked(claims);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private void deny(String sessionId, Client client) {
        if (!clients.remove(sessionId, client)) return;
        try {
            client.socket.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignored) {
            // The authorization entry is removed even if the transport has already failed.
        }
    }
}
