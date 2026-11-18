package com.sfaasproject.stf.operations.port;

import com.sfaasproject.stf.operations.dto.V7Contract.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/** A-owned query boundary used by B; implementations never expose repositories/entities. */
public interface V7Context
        extends EquipmentCatalog, MeasurementQuery, SettingsQuery, CounterSnapshot {
    record Line(long id, String name, LineType type, int displayOrder, boolean deleted) {}

    record Equipment(
            long id,
            String code,
            String name,
            long lineId,
            long processId,
            String processName,
            int processOrder,
            EquipmentStatus status,
            OffsetDateTime statusSince,
            boolean deleted) {}

    record Item(
            long id,
            long equipmentId,
            String code,
            String name,
            DataValueType kind,
            String itemRole,
            String unit,
            int decimalPlaces,
            BigDecimal caution,
            BigDecimal critical,
            String direction,
            Integer alarmValue,
            String alarmGrade,
            BigDecimal latest,
            OffsetDateTime measuredAt,
            boolean deleted) {}

    record StatusInterval(
            long equipmentId,
            EquipmentStatus status,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt) {}

    void lockEquipment(long id);

    void resetAlarmStreaks(long dataItemId);

    List<Line> lines();

    List<Equipment> equipment();

    List<Item> items();

    List<MeasurementPoint> points(long dataItemId, OffsetDateTime from, OffsetDateTime to);

    List<StatusInterval> statuses(OffsetDateTime from, OffsetDateTime to);

    BigDecimal productionRate(long lineId, OffsetDateTime at, int windowMinutes);

    String counterQuality(long lineId, OffsetDateTime at, int windowMinutes);

    int integer(String key, int fallback);

    BigDecimal decimal(String key, BigDecimal fallback);

    UserBrief historicalUser(long id);

    String accountName(long id);

    List<Long> connectedAccounts();

    List<Long> activeAccounts();
}
