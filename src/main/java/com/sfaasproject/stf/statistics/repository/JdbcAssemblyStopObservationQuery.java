package com.sfaasproject.stf.statistics.repository;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.alarm.repository.V7AlarmRepository;
import com.sfaasproject.stf.operations.adapter.JdbcBDatabase;
import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.operations.port.V7Context;
import com.sfaasproject.stf.statistics.port.AssemblyStopObservationQuery;

import java.util.*;

/** Reads B stock/alarm rows. A catalog/state/settings are accessed only through V7Context. */
public class JdbcAssemblyStopObservationQuery implements AssemblyStopObservationQuery {
    private final JdbcBDatabase db;
    private final V7Context context;
    private final V7AlarmRepository alarms;

    public JdbcAssemblyStopObservationQuery(
            JdbcBDatabase db, V7Context context, V7AlarmRepository alarms) {
        this.db = db;
        this.context = context;
        this.alarms = alarms;
    }

    public Observation observe() {
        var now = db.now();
        var lines = context.lines().stream().filter(l -> !l.deleted()).toList();
        var lineIds = lines.stream().map(V7Context.Line::id).toList();
        var equipment =
                context.equipment().stream()
                        .filter(e -> !e.deleted() && lineIds.contains(e.lineId()))
                        .toList();
        var assemblyIds =
                lines.stream()
                        .filter(l -> l.type() == LineType.ASSEMBLY)
                        .map(V7Context.Line::id)
                        .toList();
        var assembly = equipment.stream().filter(e -> assemblyIds.contains(e.lineId())).toList();
        if (assembly.isEmpty()) return new Observation(now, List.of(), false, null, null);
        var faults =
                alarms.activeFaults().stream()
                        .sorted(
                                Comparator.comparing(
                                                (Map<String, Object> a) ->
                                                        dateTime(a.get("occurred_at")))
                                        .thenComparingLong(a -> number(a, "alarm_id")))
                        .toList();
        var causes = new ArrayList<Cause>();
        boolean known = !assembly.isEmpty();
        for (var e : assembly) {
            if (e.status() == EquipmentStatus.COMMUNICATION_LOST
                    || e.statusSince() == null
                    || e.statusSince().isAfter(now)) {
                known = false;
                continue;
            }
            if (e.status() == EquipmentStatus.FAULT) {
                var alarm =
                        faults.stream()
                                .filter(a -> number(a, "equipment_id") == e.id())
                                .findFirst()
                                .orElse(null);
                causes.add(
                        new Cause(
                                null,
                                e.id(),
                                alarm == null ? null : number(alarm, "alarm_id"),
                                e.statusSince()));
            }
        }
        var parts = lines.stream().filter(l -> l.type() == LineType.PART).toList();
        if (parts.isEmpty()) known = false;
        for (var line : parts) {
            var stock =
                    db.one(
                            "SELECT qty,recorded_at FROM stock_history WHERE line_id=? AND"
                                + " recorded_at<=? ORDER BY recorded_at DESC LIMIT 1",
                            line.id(),
                            time(now));
            if (stock == null || number(stock, "qty") < 0) {
                known = false;
                continue;
            }
            if (number(stock, "qty") == 0) {
                var ids =
                        equipment.stream()
                                .filter(e -> e.lineId() == line.id())
                                .map(V7Context.Equipment::id)
                                .toList();
                var alarm =
                        faults.stream()
                                .filter(a -> ids.contains(number(a, "equipment_id")))
                                .findFirst()
                                .orElse(null);
                causes.add(
                        new Cause(
                                line.id(),
                                alarm == null ? null : number(alarm, "equipment_id"),
                                alarm == null ? null : number(alarm, "alarm_id"),
                                dateTime(stock.get("recorded_at"))));
            }
        }
        causes.sort(
                Comparator.comparing(Cause::evidenceAt)
                        .thenComparing(
                                c -> c.alarmId(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                c -> c.shortageLineId(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                c -> c.equipmentId(),
                                Comparator.nullsLast(Comparator.naturalOrder())));
        var output = context.decimal("HOURLY_OUTPUT", null);
        var price = context.decimal("UNIT_PRICE", null);
        return new Observation(
                now,
                List.copyOf(causes),
                known && causes.isEmpty(),
                output == null ? null : output.intValueExact(),
                price == null ? null : price.longValueExact());
    }
}
