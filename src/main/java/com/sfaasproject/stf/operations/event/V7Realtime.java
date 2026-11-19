package com.sfaasproject.stf.operations.event;

import static com.sfaasproject.stf.operations.service.V7Support.*;

import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.operations.port.BPorts.*;
import com.sfaasproject.stf.operations.port.V7Context;
import com.sfaasproject.stf.telegram.port.NotificationPort;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Only six v7 destinations are published, after successful commit via the durable outbox. */
public class V7Realtime {
    private final SimpMessagingTemplate socket;
    private final V7Context context;
    private final Telegram connections;
    private final NotificationPort notifications;
    private final EventClaims claims;
    private final BEventPublisher events;

    public V7Realtime(
            SimpMessagingTemplate socket,
            V7Context context,
            Telegram connections,
            NotificationPort notifications,
            EventClaims claims,
            BEventPublisher events) {
        this.socket = socket;
        this.context = context;
        this.connections = connections;
        this.notifications = notifications;
        this.claims = claims;
        this.events = events;
    }

    @EventListener
    @Transactional
    public void alarm(V7Events.AlarmCommitted e) {
        socket.convertAndSend(
                "/topic/alarms",
                dto(
                        map(
                                "eventId",
                                e.eventId(),
                                "sequence",
                                e.sequence(),
                                "alarmId",
                                e.alarmId(),
                                "eventType",
                                e.type(),
                                "grade",
                                e.grade(),
                                "processStatus",
                                e.processStatus(),
                                "occurredAt",
                                e.occurredAt()),
                        AlarmEvent.class));
        if (e.type().equals("CREATED")
                || e.type().equals("GRADE_ESCALATED")
                || e.type().equals("CLOSED")) {
            var type = e.type().equals("CLOSED") ? "ALARM_CLOSED" : e.type();
            var payload =
                    dto(
                            map(
                                    "eventId",
                                    e.eventId(),
                                    "sequence",
                                    e.sequence(),
                                    "type",
                                    type,
                                    "alarmId",
                                    e.alarmId(),
                                    "occurredAt",
                                    e.occurredAt()),
                            PersonalEvent.class);
            for (long user : context.activeAccounts())
                socket.convertAndSendToUser(Long.toString(user), "/queue/notifications", payload);
        }
        if (e.type().equals("CREATED") || e.type().equals("GRADE_ESCALATED")) {
            // Send only after all alarm listeners commit, so a failed sibling cannot cause a resend.
            events.publish(new V7Events.AlarmDeliveryRequested(e.eventId(), e.alarmId(), e.type()));
        }
    }

    @EventListener
    @Transactional
    public void deliver(V7Events.AlarmDeliveryRequested e) {
        for (long user : context.connectedAccounts()) {
            var c = connections.connection(user);
            if (c == null) continue;
            if (claims.claim("v7:telegram:" + e.eventId() + ":" + user))
                notifications.deliver(
                        new NotificationPort.Recipient(user, context.accountName(user), c.chatId()),
                        e.alarmId(),
                        e.type(),
                        "설비 알람이 발생했습니다. 관제 화면에서 확인해 주세요.");
        }
    }

    @EventListener
    public void counts(V7Events.CountsCommitted e) {
        socket.convertAndSend("/topic/alarm-counts", dto(e, AlarmCountEvent.class));
    }

    @EventListener
    public void equipment(V7Events.EquipmentCommitted e) {
        socket.convertAndSend("/topic/equipment-status", dto(e, EquipmentEvent.class));
    }

    @EventListener
    public void measurement(V7Events.MeasurementCommitted e) {
        socket.convertAndSend(
                "/topic/measurements/" + e.equipmentId(), dto(e, MeasurementEvent.class));
    }

    @EventListener
    public void inventory(V7Events.InventoryRecalculated e) {
        socket.convertAndSend("/topic/inventory", dto(e, InventoryEvent.class));
    }
}
