package com.sfaasproject.stf.operations.config;

import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.auth.security.AccessTokenAuthentication;
import com.sfaasproject.stf.operations.port.BPorts.Users;
import com.sfaasproject.stf.operations.service.BWebSocketSessions;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.time.Clock;

@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
public class BWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    private final com.sfaasproject.stf.operations.service.BTokenGuard tokens;
    private final JwtAccessTokenService jwt;
    private final Users users;
    private final Clock clock;
    private final String[] origins;
    private final BWebSocketSessions sessions;

    public BWebSocketConfiguration(
            JwtAccessTokenService jwt,
            Users users,
            Clock backendBClock,
            com.sfaasproject.stf.operations.service.BTokenGuard tokens,
            BWebSocketSessions sessions,
            @Value(
                            "${stf.websocket.allowed-origins:http://localhost:5173,http://192.168.20.11:5173}")
                    String allowed) {
        this.tokens = tokens;
        this.sessions = sessions;
        this.jwt = jwt;
        this.users = users;
        this.clock = backendBClock;
        this.origins = allowed.split(",");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry endpoints) {
        endpoints.addEndpoint("/ws").setAllowedOrigins(origins);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession socket) throws Exception {
                sessions.opened(socket);
                try {
                    super.afterConnectionEstablished(socket);
                } catch (Exception failure) {
                    sessions.closed(socket.getId());
                    throw failure;
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) throws Exception {
                try {
                    super.afterConnectionClosed(socket, status);
                } finally {
                    sessions.closed(socket.getId());
                }
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.MESSAGE
                        && !sessions.permitDelivery(SimpMessageHeaderAccessor.getSessionId(message.getHeaders())))
                    return null;
                return message;
            }
        });
    }

    @Bean
    public ThreadPoolTaskScheduler bHeartbeatScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stf-ws-");
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry broker) {
        broker.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10000, 10000})
                .setTaskScheduler(bHeartbeatScheduler());
        broker.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(
                new ChannelInterceptor() {
                    @Override
                    public Message<?> preSend(Message<?> message, MessageChannel channel) {
                        var headers =
                                MessageHeaderAccessor.getAccessor(
                                        message, StompHeaderAccessor.class);
                        if (headers == null) return message;
                        if (headers.getCommand() == StompCommand.CONNECT) {
                            String auth = headers.getFirstNativeHeader("Authorization");
                            if (auth == null || !auth.startsWith("Bearer "))
                                throw new MessageDeliveryException("Authentication required");
                            try {
                                var claims = jwt.verify(auth.substring(7), clock.instant());
                                var user = users.active(claims.userId());
                                if (user == null || users.passwordChangeRequired(claims.userId()) || tokens.revoked(claims))
                                    throw new MessageDeliveryException("Authentication required");
                                var current = new JwtAccessTokenService.Claims(claims.userId(),
                                        com.sfaasproject.stf.auth.service.AuthPolicyService.Permission.valueOf(user.position().name()),
                                        claims.jti(), claims.issuedAt(), claims.expiresAt());
                                headers.setUser(new AccessTokenAuthentication(current));
                                sessions.authenticated(headers.getSessionId(), current);
                            } catch (RuntimeException invalid) {
                                throw new MessageDeliveryException("Authentication required");
                            }
                        } else if (headers.getCommand() == StompCommand.SUBSCRIBE) {
                            if (!(headers.getUser() instanceof AccessTokenAuthentication session))
                                throw new MessageDeliveryException("Subscription denied");
                            try {
                                var claims = session.getPrincipal();
                                if (!claims.expiresAt().isAfter(clock.instant())
                                        || users.active(claims.userId()) == null || users.passwordChangeRequired(claims.userId()) || tokens.revoked(claims))
                                    throw new MessageDeliveryException("Subscription denied");
                            } catch (RuntimeException invalid) {
                                throw new MessageDeliveryException("Subscription denied");
                            }
                            String destination = headers.getDestination();
                            if (headers.getUser() == null
                                    || destination == null
                                    || !(destination.matches(
                                                    "/topic/(alarms|alarm-counts|inventory|equipment-status|measurements/[1-9][0-9]*)")
                                            || destination.equals("/user/queue/notifications")))
                                throw new MessageDeliveryException("Subscription denied");
                        } else if (headers.getCommand() == StompCommand.SEND)
                            throw new MessageDeliveryException("Client publishing is disabled");
                        return message;
                    }
                });
    }
}
