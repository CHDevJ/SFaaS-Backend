package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.event.*;
import com.sfaasproject.stf.operations.port.BPorts.*;
import com.sfaasproject.stf.operations.scheduler.TelegramPollingJob;
import com.sfaasproject.stf.operations.service.*;
import com.sfaasproject.stf.telegram.service.*;

import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.*;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;

class BEventAndTelegramTest {
    final OffsetDateTime now = OffsetDateTime.parse("2026-11-17T09:00:00+09:00");
    final UnitOfWork direct =
            new UnitOfWork() {
                public <T> T execute(Supplier<T> s) {
                    return s.get();
                }
            };

    @Test
    void deliveryDefaultDateUsesKoreaEvenWhenTheSharedClockIsUtc() {
        var store = mock(Telegram.class);
        var service =
                new TelegramApplicationService(
                        store,
                        mock(LinkTokens.class),
                        mock(Users.class),
                        mock(TelegramLinkTokenService.class),
                        mock(TelegramDeliveryService.class),
                        Clock.fixed(Instant.parse("2026-11-17T16:00:00Z"), ZoneOffset.UTC),
                        "");
        var holder = org.springframework.security.core.context.SecurityContextHolder.getContext();
        holder.setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "1",
                        "",
                        List.of(
                                new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            service.getTelegramDeliveries(null, null, 0, 20);
            verify(store).deliveries(LocalDate.of(2026, 11, 18), null, 0, 20);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void eventsAreDeferredUntilCommitAndAbsentAfterRollback() {
        var publisher = mock(ApplicationEventPublisher.class);
        var events = new BEventPublisher(publisher);
        Object event = new BEvents.DutyChanged("id", 1, 2, now);
        TransactionSynchronizationManager.initSynchronization();
        try {
            events.publish(event);
            verifyNoInteractions(publisher);
            for (var sync : TransactionSynchronizationManager.getSynchronizations())
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verifyNoInteractions(publisher);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.initSynchronization();
        try {
            events.publish(event);
            verifyNoInteractions(publisher);
            for (var sync : TransactionSynchronizationManager.getSynchronizations())
                sync.afterCommit();
            verify(publisher).publishEvent(event);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void productionAndValueEventsAreIdempotent() {
        Set<String> seen = new HashSet<>();
        EventClaims claims = seen::add;
        var inventory = mock(InventoryRuntime.class);
        var alarms = mock(AlarmApplicationService.class);
        var handler =
                new BEventHandlers(
                        claims,
                        direct,
                        inventory,
                        alarms,
                        mock(TelegramApplicationService.class),
                        mock(Users.class),
                        mock(Telegram.class),
                        mock(SimpMessagingTemplate.class));
        var production = new BEvents.ProductionRecorded("p1", 7, 3, 2, true, now);
        handler.production(production);
        handler.production(production);
        verify(inventory, times(1)).applyProduction("p1", 7, 2, now);
        verify(alarms, times(1)).inspectorPassed(3, 2, now);
        var recovered = new BEvents.AlarmValueChanged("v1", 1, true, now);
        handler.value(recovered);
        handler.value(recovered);
        verify(alarms, times(1)).valueChanged(1, true, now);
    }

    @Test
    void notificationFailureCannotUndoCommittedAlarmAndSocketUsesPrivateQueue() {
        var telegram = mock(TelegramApplicationService.class);
        var connections = mock(Telegram.class);
        var socket = mock(SimpMessagingTemplate.class);
        when(connections.connection(2)).thenReturn(new Connection("42", now));
        when(telegram.send(2L, 1L, "CREATED", "message"))
                .thenThrow(new IllegalStateException("failure"));
        var handler =
                new BEventHandlers(
                        id -> true,
                        direct,
                        mock(InventoryRuntime.class),
                        mock(AlarmApplicationService.class),
                        telegram,
                        mock(Users.class),
                        connections,
                        socket);
        var event = new BEvents.Notification("n1", 1, 2, "CREATED", 1L, "message", now);
        assertDoesNotThrow(() -> handler.notification(event));
        verify(socket)
                .convertAndSendToUser(
                        "2", "/queue/notifications", BStompMessages.notification(event));
        verify(telegram).send(2L, 1L, "CREATED", "message");
    }

    @Test
    void pollingAdvancesOffsetAndDoesNotRepeatUpdates() {
        var service = mock(TelegramApplicationService.class);
        Set<String> seen = new HashSet<>();
        String json =
                "{\"ok\":true,\"result\":[{\"update_id\":7,\"message\":{\"chat\":{\"id\":42,\"type\":\"private\"},\"text\":\"/start"
                    + " token\"}}]}";
        var poller = new TelegramPollingJob(offset -> json, service, seen::add, direct);
        poller.poll();
        poller.poll();
        assertEquals(8, poller.nextOffset());
        verify(service, times(1)).start("42", "private", "/start token");
    }

    @Test
    void pendingSignupCanLinkOnlyOnceAndGroupsCannotBindTokens() {
        var store = mock(Telegram.class);
        var tokens = mock(LinkTokens.class);
        var users = mock(Users.class);
        when(tokens.consume("token")).thenReturn(7L, (Long) null);
        when(users.connectable(7)).thenReturn(true);
        var service =
                new TelegramApplicationService(
                        store,
                        tokens,
                        users,
                        new TelegramLinkTokenService(),
                        new TelegramDeliveryService((c, m) -> {}, duration -> {}),
                        Clock.fixed(now.toInstant(), ZoneId.of("Asia/Seoul")),
                        "stf_bot");
        assertFalse(service.start("42", "group", "/start token"));
        verifyNoInteractions(tokens);
        assertTrue(service.start("42", "private", "/start token"));
        assertFalse(service.start("42", "private", "/start token"));
        verify(store, times(1)).link(7, "42", now);
    }

    @Test
    void longPollingFailureDoesNotExposeOrAdvanceTokenState() {
        var poller =
                new TelegramPollingJob(
                        offset -> {
                            throw new IllegalStateException("remote failure");
                        },
                        mock(TelegramApplicationService.class),
                        id -> true,
                        direct);
        assertDoesNotThrow(poller::poll);
        assertEquals(0, poller.nextOffset());
    }
}
