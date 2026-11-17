package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.auth.security.AccessTokenAuthentication;
import com.sfaasproject.stf.operations.config.BWebSocketConfiguration;
import com.sfaasproject.stf.operations.dto.BContract.Position;
import com.sfaasproject.stf.operations.dto.BContract.UserBrief;
import com.sfaasproject.stf.operations.port.BPorts.Users;
import com.sfaasproject.stf.operations.service.BTokenGuard;

import org.junit.jupiter.api.*;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;

class BWebSocketSecurityTest {
    static class Registration extends ChannelRegistration {
        List<ChannelInterceptor> all() {
            return getInterceptors();
        }
    }

    final Clock clock = Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    final JwtAccessTokenService jwt =
            new JwtAccessTokenService(
                    "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "stf");
    final BTokenGuard tokens = mock(BTokenGuard.class);
    final Users users = mock(Users.class);
    ChannelInterceptor interceptor;

    @BeforeEach
    void setup() {
        var config =
                new BWebSocketConfiguration(jwt, users, clock, tokens,
                        new com.sfaasproject.stf.operations.service.BWebSocketSessions(users, tokens, clock),
                        "http://localhost:5173");
        var registration = new Registration();
        config.configureClientInboundChannel(registration);
        interceptor = registration.all().get(0);
        when(users.active(1)).thenReturn(new UserBrief(1L, "사용자", Position.LINE_MANAGER));
    }

    Message<byte[]> message(StompHeaderAccessor headers) {
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test
    void connectsOnlyWithActiveNonRevokedJwt() {
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader(
                "Authorization",
                "Bearer "
                        + jwt.issue(
                                        1,
                                        com.sfaasproject.stf.auth.service.AuthPolicyService.Position
                                                .LINE_MANAGER,
                                        clock.instant())
                                .value());
        assertNotNull(interceptor.preSend(message(headers), mock(MessageChannel.class)));
        assertEquals("1", headers.getUser().getName());
        when(tokens.revoked(any())).thenReturn(true);
        assertThrows(
                MessageDeliveryException.class,
                () -> interceptor.preSend(message(headers), mock(MessageChannel.class)));
    }

    @Test
    void privateQueueCannotSubscribeAsAnotherUser() {
        var own = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        own.setUser(new AccessTokenAuthentication(new JwtAccessTokenService.Claims(1,
                com.sfaasproject.stf.auth.service.AuthPolicyService.Position.LINE_MANAGER,
                "stomp-test",clock.instant(),clock.instant().plusSeconds(1800))));
        own.setDestination("/user/queue/notifications");
        assertNotNull(interceptor.preSend(message(own), mock(MessageChannel.class)));
        own.setDestination("/user/2/queue/notifications");
        assertThrows(
                MessageDeliveryException.class,
                () -> interceptor.preSend(message(own), mock(MessageChannel.class)));
    }

    @Test
    void anonymousConnectAndClientPublishingAreRejected() {
        assertThrows(
                MessageDeliveryException.class,
                () ->
                        interceptor.preSend(
                                message(StompHeaderAccessor.create(StompCommand.CONNECT)),
                                mock(MessageChannel.class)));
        var send = StompHeaderAccessor.create(StompCommand.SEND);
        send.setUser(() -> "1");
        send.setDestination("/topic/alarms");
        assertThrows(
                MessageDeliveryException.class,
                () -> interceptor.preSend(message(send), mock(MessageChannel.class)));
    }

    @Test void logoutAfterConnectPreventsNewSubscription() {
        var headers=StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("Authorization","Bearer "+jwt.issue(1,
                com.sfaasproject.stf.auth.service.AuthPolicyService.Position.LINE_MANAGER,clock.instant()).value());
        interceptor.preSend(message(headers),mock(MessageChannel.class));
        var subscribe=StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setUser(headers.getUser()); subscribe.setDestination("/topic/alarms");
        when(tokens.revoked(any())).thenReturn(true);
        assertThrows(MessageDeliveryException.class,()->interceptor.preSend(message(subscribe),mock(MessageChannel.class)));
    }
    @Test void expiredConnectedPrincipalCannotSubscribe() {
        var subscribe=StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setUser(new AccessTokenAuthentication(new JwtAccessTokenService.Claims(1,
                com.sfaasproject.stf.auth.service.AuthPolicyService.Position.LINE_MANAGER,"expired",
                clock.instant().minusSeconds(1800),clock.instant())));
        subscribe.setDestination("/user/queue/notifications");
        assertThrows(MessageDeliveryException.class,()->interceptor.preSend(message(subscribe),mock(MessageChannel.class)));
    }
}
