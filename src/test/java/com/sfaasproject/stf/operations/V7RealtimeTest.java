package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.operations.event.*;
import com.sfaasproject.stf.operations.port.*;
import com.sfaasproject.stf.operations.port.BPorts.*;
import com.sfaasproject.stf.telegram.port.NotificationPort;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.*;
import java.util.*;

class V7RealtimeTest {
    final OffsetDateTime now = OffsetDateTime.parse("2026-11-17T10:00:00+09:00");

    @Test
    void occurrenceGoesToAllConnectedUndeletedAccountsAndDeliveryIsDeduplicated() {
        var socket = mock(SimpMessagingTemplate.class);
        var context = mock(V7Context.class);
        var telegram = mock(Telegram.class);
        var notifications = mock(NotificationPort.class);
        Set<String> seen = new HashSet<>();
        when(context.connectedAccounts()).thenReturn(List.of(1L, 2L, 3L));
        when(context.activeAccounts()).thenReturn(List.of(1L, 2L));
        for (long id : List.of(1L, 2L, 3L)) {
            when(context.accountName(id)).thenReturn("계정" + id);
            when(telegram.connection(id)).thenReturn(new Connection("chat" + id, now));
        }
        var realtime = new V7Realtime(socket, context, telegram, notifications, seen::add);
        var event =
                new V7Events.AlarmCommitted(
                        "v7-event",
                        1,
                        7,
                        "CREATED",
                        AlarmGrade.CRITICAL,
                        AlarmProcessStatus.ACTIVE,
                        now);
        realtime.alarm(event);
        realtime.alarm(event);
        verify(notifications, times(3)).deliver(any(), eq(7L), eq("CREATED"), anyString());
        verify(socket, times(2))
                .convertAndSendToUser(
                        eq("1"), eq("/queue/notifications"), any(PersonalEvent.class));
        verify(socket, never()).convertAndSendToUser(eq("3"), anyString(), any());
    }

    @Test
    void recoveryAndClosureNeverSendTelegramAndClosureUsesNoUnreadDatabaseState() {
        var socket = mock(SimpMessagingTemplate.class);
        var context = mock(V7Context.class);
        when(context.activeAccounts()).thenReturn(List.of(1L));
        var notifications = mock(NotificationPort.class);
        var realtime =
                new V7Realtime(socket, context, mock(Telegram.class), notifications, id -> true);
        realtime.alarm(
                new V7Events.AlarmCommitted(
                        "recovered",
                        1,
                        7,
                        "VALUE_RECOVERED",
                        AlarmGrade.CRITICAL,
                        AlarmProcessStatus.ACTIVE,
                        now));
        realtime.alarm(
                new V7Events.AlarmCommitted(
                        "closed",
                        2,
                        7,
                        "CLOSED",
                        AlarmGrade.CRITICAL,
                        AlarmProcessStatus.CLOSED,
                        now));
        verifyNoInteractions(notifications);
        var event = ArgumentCaptor.forClass(PersonalEvent.class);
        verify(socket).convertAndSendToUser(eq("1"), eq("/queue/notifications"), event.capture());
        assertEquals(PersonalEventType.ALARM_CLOSED, event.getValue().type());
        assertFalse(
                Arrays.stream(PersonalEvent.class.getRecordComponents())
                        .anyMatch(f -> f.getName().equals("unreadCount")));
    }
}
