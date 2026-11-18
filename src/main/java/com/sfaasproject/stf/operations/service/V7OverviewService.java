package com.sfaasproject.stf.operations.service;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;
import static com.sfaasproject.stf.operations.service.V7Support.*;

import com.sfaasproject.stf.alarm.service.V7AlarmService;
import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.operations.port.V7Context;

import java.util.*;

public class V7OverviewService {
    private final V7Context context;
    private final V7AlarmService alarms;

    public V7OverviewService(V7Context context, V7AlarmService alarms) {
        this.context = context;
        this.alarms = alarms;
    }

    public EquipmentLayoutResponse layout(Long line) {
        id(line);
        var lines =
                context.lines().stream()
                        .filter(l -> !l.deleted())
                        .collect(java.util.stream.Collectors.toMap(V7Context.Line::id, l -> l));
        var equipment =
                context.equipment().stream()
                        .filter(
                                e ->
                                        !e.deleted()
                                                && lines.containsKey(e.lineId())
                                                && (line == null || e.lineId() == line))
                        .toList();
        var rows = new ArrayList<Map<String, Object>>();
        var items = context.items();
        var active =
                alarms.rows(null, null, null).stream()
                        .filter(a -> "ACTIVE".equals(a.get("process_state")))
                        .toList();
        for (var e : equipment) {
            var measurements = new ArrayList<Map<String, Object>>();
            var abnormal = new ArrayList<Map<String, Object>>();
            for (var d :
                    items.stream()
                            .filter(d -> !d.deleted() && d.equipmentId() == e.id())
                            .toList()) {
                if (d.latest() == null || d.measuredAt() == null) continue;
                String grade = null;
                if (d.latest() != null) {
                    if (d.kind() == DataValueType.MEASUREMENT
                            && d.caution() != null
                            && d.critical() != null) {
                        boolean above = "ABOVE".equals(d.direction());
                        if ((above
                                ? d.latest().compareTo(d.critical()) >= 0
                                : d.latest().compareTo(d.critical()) <= 0)) grade = "CRITICAL";
                        else if (above
                                ? d.latest().compareTo(d.caution()) >= 0
                                : d.latest().compareTo(d.caution()) <= 0) grade = "CAUTION";
                    } else if (d.kind() == DataValueType.STATE
                            && d.alarmValue() != null
                            && d.latest().intValue() == d.alarmValue()) grade = d.alarmGrade();
                }
                var m =
                        com.sfaasproject.stf.operations.service.V7Support.map(
                                "dataItemId",
                                d.id(),
                                "dataItemName",
                                d.name(),
                                "valueType",
                                d.kind(),
                                "value",
                                d.latest(),
                                "displayValue",
                                d.latest() == null ? null : d.latest().toPlainString(),
                                "unit",
                                d.unit(),
                                "valueStatus",
                                grade == null ? "NORMAL" : grade,
                                "cautionThreshold",
                                d.caution(),
                                "criticalThreshold",
                                d.critical(),
                                "measuredAt",
                                d.measuredAt());
                measurements.add(m);
                if (grade != null)
                    abnormal.add(
                            com.sfaasproject.stf.operations.service.V7Support.map(
                                    "dataItemId",
                                    d.id(),
                                    "dataItemName",
                                    d.name(),
                                    "currentValue",
                                    d.latest(),
                                    "unit",
                                    d.unit(),
                                    "valueStatus",
                                    grade,
                                    "threshold",
                                    d.kind() == DataValueType.STATE
                                            ? d.alarmValue()
                                            : grade.equals("CRITICAL")
                                                    ? d.critical()
                                                    : d.caution()));
            }
            rows.add(
                    com.sfaasproject.stf.operations.service.V7Support.map(
                            "equipmentId",
                            e.id(),
                            "equipmentCode",
                            e.code(),
                            "equipmentName",
                            e.name(),
                            "lineId",
                            e.lineId(),
                            "lineName",
                            lines.get(e.lineId()).name(),
                            "lineType",
                            lines.get(e.lineId()).type(),
                            "processId",
                            e.processId(),
                            "processName",
                            e.processName(),
                            "processOrder",
                            e.processOrder(),
                            "status",
                            e.status(),
                            "statusStartedAt",
                            e.statusSince(),
                            "activeAlarmCount",
                            active.stream()
                                    .filter(a -> number(a, "equipment_id") == e.id())
                                    .count(),
                            "mainMeasurements",
                            measurements.stream().limit(3).toList(),
                            "abnormalMeasurements",
                            abnormal));
        }
        var counts =
                Arrays.stream(EquipmentStatus.values())
                        .map(
                                s ->
                                        com.sfaasproject.stf.operations.service.V7Support.map(
                                                "status",
                                                s,
                                                "count",
                                                equipment.stream()
                                                        .filter(e -> e.status() == s)
                                                        .count()))
                        .toList();
        var groups =
                equipment.stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        V7Context.Equipment::processId,
                                        LinkedHashMap::new,
                                        java.util.stream.Collectors.toList()))
                        .values()
                        .stream()
                        .map(
                                g ->
                                        com.sfaasproject.stf.operations.service.V7Support.map(
                                                "processId",
                                                g.getFirst().processId(),
                                                "lineId",
                                                g.getFirst().lineId(),
                                                "processOrder",
                                                g.getFirst().processOrder(),
                                                "equipmentCount",
                                                g.size(),
                                                "abnormalEquipmentCount",
                                                g.stream()
                                                        .filter(
                                                                e ->
                                                                        e.status()
                                                                                        == EquipmentStatus
                                                                                                .FAULT
                                                                                || e.status()
                                                                                        == EquipmentStatus
                                                                                                .CAUTION
                                                                                || e.status()
                                                                                        == EquipmentStatus
                                                                                                .COMMUNICATION_LOST)
                                                        .count(),
                                                "equipmentIds",
                                                g.stream().map(V7Context.Equipment::id).toList()))
                        .toList();
        var suggested =
                equipment.stream()
                        .filter(
                                e ->
                                        e.status() == EquipmentStatus.FAULT
                                                || e.status() == EquipmentStatus.CAUTION)
                        .sorted(
                                Comparator.comparing(
                                                V7Context.Equipment::statusSince,
                                                Comparator.nullsLast(Comparator.reverseOrder()))
                                        .thenComparingLong(V7Context.Equipment::id))
                        .findFirst()
                        .orElse(null);
        return dto(
                com.sfaasproject.stf.operations.service.V7Support.map(
                        "success",
                        true,
                        "data",
                        com.sfaasproject.stf.operations.service.V7Support.map(
                                "equipment",
                                rows,
                                "statusCounts",
                                counts,
                                "processGroups",
                                groups,
                                "suggestedEquipmentId",
                                suggested == null ? null : suggested.id())),
                EquipmentLayoutResponse.class);
    }
}
