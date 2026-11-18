package com.sfaasproject.stf.operations.event;

import com.sfaasproject.stf.operations.dto.BContract.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** v7 wire payloads; internal/outbox events keep their persisted shape. */
public final class BStompMessages {
    private BStompMessages() {}

    public record AlarmMessage(
            String eventId, long sequence, long alarmId, String eventType,
            AlarmGrade grade, String processStatus, OffsetDateTime occurredAt) {}

    public record AlarmCountMessage(String eventId, long sequence, int activeCount) {}

    public record StageThresholds(int inventoryWarningMinutes, int inventoryDangerMinutes) {}

    public record InventoryMessage(
            String eventId, long sequence, long lineId, Long quantity, InventoryStage stage,
            BigDecimal remainingMinutes, OffsetDateTime occurredAt, StageThresholds stageThresholds) {}

    public record NotificationMessage(
            String eventId, long sequence, String type, long alarmId, OffsetDateTime occurredAt) {}

    public static AlarmMessage alarm(BEvents.AlarmChanged event) {
        String type = switch (event.eventType()) {
            case "OCCUR", "CREATED" -> "CREATED";
            case "GRADE_UP", "GRADE_ESCALATED" -> "GRADE_ESCALATED";
            case "RECOVER", "VALUE_RECOVERED" -> "VALUE_RECOVERED";
            case "CLOSE", "CLOSED" -> "CLOSED";
            default -> null;
        };
        if (type == null) return null;
        return new AlarmMessage(
                event.eventId(), event.sequence(), event.alarmId(), type, event.grade(),
                event.processStatus().name().equals("CLOSED") ? "CLOSED" : "ACTIVE",
                event.occurredAt());
    }

    public static InventoryMessage inventory(BEvents.InventoryChanged event, StageThresholds thresholds) {
        BigDecimal remaining = event.remainingMinutes();
        InventoryStage stage = remaining == null
                ? (event.stage() == null ? null : InventoryStage.MAINTAIN)
                : remaining.compareTo(BigDecimal.valueOf(thresholds.inventoryDangerMinutes())) <= 0
                        ? InventoryStage.DANGER
                        : remaining.compareTo(BigDecimal.valueOf(thresholds.inventoryWarningMinutes())) <= 0
                                ? InventoryStage.CAUTION : InventoryStage.MAINTAIN;
        return new InventoryMessage(
                event.eventId(), event.sequence(), event.lineId(), event.quantity(), stage,
                remaining, event.occurredAt(), thresholds);
    }

    public static NotificationMessage notification(BEvents.Notification event) {
        if (event.alarmId() == null || event.alarmId() <= 0) return null;
        String type = switch (event.type()) {
            case "OCCUR", "CREATED" -> "CREATED";
            case "GRADE_UP", "GRADE_ESCALATED" -> "GRADE_ESCALATED";
            case "CLOSE", "CLOSED", "ALARM_CLOSED" -> "ALARM_CLOSED";
            default -> null;
        };
        return type == null ? null : new NotificationMessage(
                event.eventId(), event.sequence(), type, event.alarmId(), event.occurredAt());
    }
}
