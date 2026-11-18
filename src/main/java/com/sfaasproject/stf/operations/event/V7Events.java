package com.sfaasproject.stf.operations.event;

import com.sfaasproject.stf.operations.dto.V7Contract.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Persisted domain events. Wire DTOs are built by V7Realtime only after commit. */
public final class V7Events {
    private V7Events() {}

    public record AlarmCommitted(
            String eventId,
            long sequence,
            long alarmId,
            String type,
            AlarmGrade grade,
            AlarmProcessStatus processStatus,
            OffsetDateTime occurredAt) {}

    public record CountsCommitted(String eventId, long sequence, int activeCount) {}

    public record EquipmentCommitted(
            String eventId,
            long sequence,
            long equipmentId,
            EquipmentStatus status,
            OffsetDateTime statusStartedAt,
            OffsetDateTime occurredAt) {}

    public record MeasurementCommitted(
            String eventId,
            long sequence,
            long equipmentId,
            long dataItemId,
            DataValueType valueType,
            BigDecimal value,
            OffsetDateTime measuredAt) {}

    public record InventoryRecalculated(
            String eventId,
            long sequence,
            long lineId,
            Long quantity,
            String stage,
            BigDecimal remainingMinutes,
            OffsetDateTime occurredAt,
            InventoryStageThresholds stageThresholds) {}
}
