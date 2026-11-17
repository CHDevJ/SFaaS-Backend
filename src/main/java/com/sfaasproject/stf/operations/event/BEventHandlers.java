package com.sfaasproject.stf.operations.event;

import com.sfaasproject.stf.operations.port.BPorts.*;
import com.sfaasproject.stf.operations.service.*;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class BEventHandlers {
    private final EventClaims claims;
    private final UnitOfWork tx;
    private final InventoryRuntime inventory;
    private final AlarmApplicationService alarms;
    private final TelegramApplicationService telegram;
    private final Users users;
    private final Telegram connections;
    private final SimpMessagingTemplate socket;
    private final Settings settings;

    public BEventHandlers(
            EventClaims claims,
            UnitOfWork tx,
            InventoryRuntime inventory,
            AlarmApplicationService alarms,
            TelegramApplicationService telegram,
            Users users,
            Telegram connections,
            SimpMessagingTemplate socket) {
        this(claims, tx, inventory, alarms, telegram, users, connections, socket, null);
    }

    public BEventHandlers(
            EventClaims claims,
            UnitOfWork tx,
            InventoryRuntime inventory,
            AlarmApplicationService alarms,
            TelegramApplicationService telegram,
            Users users,
            Telegram connections,
            SimpMessagingTemplate socket,
            Settings settings) {
        this.claims = claims;
        this.tx = tx;
        this.inventory = inventory;
        this.alarms = alarms;
        this.telegram = telegram;
        this.users = users;
        this.connections = connections;
        this.socket = socket;
        this.settings = settings;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void production(BEvents.ProductionRecorded event) {
        if (event.quantity() < 0) throw Checks.invalid("Production quantity cannot be negative");
        tx.execute(
                () -> {
                    if (!claims.claim("production:" + event.eventId())) return null;
                    inventory.applyProduction(
                            event.eventId(),
                            event.equipmentId(),
                            event.quantity(),
                            event.occurredAt());
                    if (event.lineEndInspector())
                        alarms.inspectorPassed(
                                event.lineId(), event.quantity(), event.occurredAt());
                    return null;
                });
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void value(BEvents.AlarmValueChanged event) {
        tx.execute(
                () -> {
                    if (claims.claim("value:" + event.eventId()))
                        alarms.valueChanged(event.alarmId(), event.recovered(), event.occurredAt());
                    return null;
                });
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void alarm(BEvents.AlarmChanged event) {
        var message = BStompMessages.alarm(event);
        if (message != null) socket.convertAndSend("/topic/alarms", message);
        if (event.eventType().equals("OCCUR")
                || event.eventType().equals("GRADE_UP")
                || event.eventType().equals("RENOTIFY")) {
            for (var user : users.notificationUsers()) {
                if (user.position().name().equals("USER")
                        || event.eventType().equals("RENOTIFY")
                                && user.position().name().equals("ADMIN"))
                    notifySafely(
                            event.eventId(),
                            user.userId(),
                            event.alarmId(),
                            event.eventType(),
                            "설비 알람이 발생했습니다. 관제 화면에서 확인해 주세요.");
            }
        }
    }

    @EventListener
    public void equipment(BEvents.EquipmentChanged event) {
        socket.convertAndSend("/topic/equipment-status", event);
    }

    @EventListener
    public void measurement(BEvents.MeasurementChanged event) {
        socket.convertAndSend("/topic/measurements/" + event.equipmentId(), event);
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void counts(BEvents.AlarmCounts event) {
        socket.convertAndSend(
                "/topic/alarm-counts",
                new BStompMessages.AlarmCountMessage(
                        event.eventId(), event.sequence(), alarms.activeCount()));
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void effect(BEvents.EffectChanged event) {
        // Keep old outbox events consumable; the removed v5 topic is not public in v7.
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void inventory(BEvents.InventoryChanged event) {
        var thresholds = new BStompMessages.StageThresholds(
                settings == null ? 30 : settings.integer("STOCK_LEVEL_MIN", 30),
                settings == null ? 10 : settings.integer("STOCK_DANGER_MIN", 10));
        socket.convertAndSend("/topic/inventory", BStompMessages.inventory(event, thresholds));
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notification(BEvents.Notification event) {
        var message = BStompMessages.notification(event);
        if (message != null)
            socket.convertAndSendToUser(Long.toString(event.userId()), "/queue/notifications", message);
        notifySafely(
                event.eventId(), event.userId(), event.alarmId(), event.type(), event.message());
    }

    private void notifySafely(String eventId, long id, Long alarmId, String type, String message) {
        if (connections.connection(id) == null) return;
        try {
            if (claims.claim("delivery:" + eventId + ":" + id))
                telegram.send(id, alarmId, type, message);
        } catch (RuntimeException failure) {
            // The originating transaction is already committed. Delivery failure cannot undo it.
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn("Notification delivery failed for alarm {}", alarmId);
        }
    }
}
