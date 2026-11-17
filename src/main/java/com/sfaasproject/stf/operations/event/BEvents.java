package com.sfaasproject.stf.operations.event;

import com.sfaasproject.stf.operations.dto.BContract.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class BEvents {
    private BEvents() {}

    public record EquipmentChanged(
            String eventId,
            long sequence,
            long equipmentId,
            EquipmentStatus status,
            OffsetDateTime statusStartedAt,
            OffsetDateTime occurredAt) {}

    public record MeasurementChanged(
            String eventId,
            long sequence,
            long equipmentId,
            long dataItemId,
            DataValueType valueType,
            BigDecimal value,
            OffsetDateTime measuredAt) {}

    public record AlarmChanged(
            String eventId,
            long sequence,
            long alarmId,
            String eventType,
            AlarmGrade grade,
            AlarmProcessStatus processStatus,
            OffsetDateTime occurredAt) {}

    public record AlarmCounts(
            String eventId, long sequence, int unacknowledgedCount, OffsetDateTime occurredAt) {}

    public record EffectChanged(
            String eventId,
            long sequence,
            long alarmId,
            EffectStatus effectStatus,
            long currentProductionCount,
            long targetProductionCount,
            OffsetDateTime occurredAt) {}

    public record InventoryChanged(
            String eventId,
            long sequence,
            long lineId,
            long quantity,
            InventoryStage stage,
            BigDecimal remainingMinutes,
            OffsetDateTime occurredAt) {}

    public record Notification(
            String eventId,
            long sequence,
            long userId,
            String type,
            Long alarmId,
            String message,
            OffsetDateTime occurredAt) {}

    public record MeasurementStored(
            String eventId,
            long equipmentId,
            long dataItemId,
            OffsetDateTime measuredAt,
            BigDecimal value) {}

    public record ProductionRecorded(
            String eventId,
            long equipmentId,
            long lineId,
            int quantity,
            boolean lineEndInspector,
            OffsetDateTime occurredAt) {}

    public record AlarmValueChanged(
            String eventId, long alarmId, boolean recovered, OffsetDateTime occurredAt) {}

    public record DutyChanged(
            String eventId, long scheduleId, long userId, OffsetDateTime occurredAt) {}

    public static String id() {
        return UUID.randomUUID().toString();
    }
}
