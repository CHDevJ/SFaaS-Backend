package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService.Claims;
import com.sfaasproject.stf.operations.config.BWebSocketConfiguration;
import com.sfaasproject.stf.operations.dto.BContract.UserBrief;
import com.sfaasproject.stf.operations.port.BPorts.Users;
import com.sfaasproject.stf.operations.service.BTokenGuard;
import com.sfaasproject.stf.operations.service.BWebSocketSessions;
import java.io.IOException;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.support.*;
import org.springframework.web.socket.*;

class BWebSocketSessionsTest {
    final Instant now = Instant.parse("2026-11-17T00:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    final Users users = mock(Users.class);
    final BTokenGuard tokens = mock(BTokenGuard.class);
    final BWebSocketSessions sessions = new BWebSocketSessions(users, tokens, clock);
    final Claims first = new Claims(1, Permission.USER, "one", now, now.plusSeconds(1800));

    @BeforeEach void setup() {
        when(users.active(1)).thenReturn(new UserBrief(1L, "보전", com.sfaasproject.stf.operations.dto.BContract.Position.USER));
    }

    WebSocketSession open(String id, Claims claims) {
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn(id);
        when(socket.isOpen()).thenReturn(true);
        sessions.opened(socket);
        if (claims != null) sessions.authenticated(id, claims);
        return socket;
    }

    @Test void activeSubscriptionCanReceiveMessages() throws Exception {
        var socket = open("s", first);
        assertTrue(sessions.permitDelivery("s"));
        verify(socket, never()).close(any());
    }

    @Test void logoutRevocationStopsExistingSubscriptionAndClosesIt() throws Exception {
        var socket = open("s", first);
        assertTrue(sessions.permitDelivery("s"));
        when(tokens.revoked(first)).thenReturn(true);
        assertFalse(sessions.permitDelivery("s"));
        assertFalse(sessions.permitDelivery("s"));
        verify(socket, times(1)).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test void expiryAtBoundaryClosesConnection() throws Exception {
        var socket = open("s", new Claims(1, Permission.USER, "expired", now.minusSeconds(1800), now));
        assertFalse(sessions.permitDelivery("s"));
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test void deletedAccountClosesConnection() throws Exception {
        var socket = open("s", first);
        when(users.active(1)).thenReturn(null);
        sessions.checkSessions();
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test void revocationStoreOutageFailsClosed() throws Exception {
        var socket = open("s", first);
        when(tokens.revoked(first)).thenThrow(new IllegalStateException("store unavailable"));
        assertFalse(sessions.permitDelivery("s"));
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test void sweepClosesRevokedIdleSessionWithoutAnOutboundEvent() throws Exception {
        var socket = open("s", first);
        when(tokens.revoked(first)).thenReturn(true);
        sessions.checkSessions();
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test void logoutOfOneSessionKeepsOtherLoginOfSameUserOpen() throws Exception {
        var one = open("one", first);
        var second = new Claims(1, Permission.USER, "two", now, now.plusSeconds(1800));
        var two = open("two", second);
        when(tokens.revoked(first)).thenReturn(true);
        sessions.checkSessions();
        verify(one).close(CloseStatus.POLICY_VIOLATION);
        verify(two, never()).close(any());
        assertTrue(sessions.permitDelivery("two"));
    }

    @Test void disconnectedSessionHasNoAuthorizationEntry() {
        open("s", first);
        sessions.closed("s");
        assertFalse(sessions.permitDelivery("s"));
        verifyNoInteractions(tokens);
    }

    @Test void brokenTransportStillRemovesAuthorizationEntry() throws Exception {
        var socket = open("s", first);
        doThrow(new IOException("disconnected")).when(socket).close(any());
        when(tokens.revoked(first)).thenReturn(true);
        assertFalse(sessions.permitDelivery("s"));
        sessions.checkSessions();
        verify(socket, times(1)).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test void unknownOrUnauthenticatedSocketCannotReceiveApplicationData() throws Exception {
        var socket = open("s", null);
        assertFalse(sessions.permitDelivery(null));
        assertFalse(sessions.permitDelivery("unknown"));
        assertFalse(sessions.permitDelivery("s"));
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    static class Registration extends ChannelRegistration {
        List<ChannelInterceptor> all() { return getInterceptors(); }
    }

    @Test void outboundInterceptorDropsRevokedMessageButAllowsProtocolFrames() {
        open("s", first);
        var config = new BWebSocketConfiguration(null, users, clock, tokens, sessions, "http://localhost:5173");
        var registration = new Registration();
        config.configureClientOutboundChannel(registration);
        var interceptor = registration.all().get(0);
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("s");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertSame(message, interceptor.preSend(message, mock(MessageChannel.class)));
        when(tokens.revoked(first)).thenReturn(true);
        assertNull(interceptor.preSend(message, mock(MessageChannel.class)));
        var connected = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT_ACK);
        connected.setSessionId("s");
        var protocol = MessageBuilder.createMessage(new byte[0], connected.getMessageHeaders());
        assertSame(protocol, interceptor.preSend(protocol, mock(MessageChannel.class)));
    }
}
