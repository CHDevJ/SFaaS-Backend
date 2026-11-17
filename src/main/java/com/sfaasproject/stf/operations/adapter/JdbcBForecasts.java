package com.sfaasproject.stf.operations.adapter;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BPorts.*;

import java.math.*;
import java.time.*;
import java.util.*;

public class JdbcBForecasts {
    private final JdbcBDatabase db;
    private final InventoryRuntime inventory;
    private final Settings settings;

    public JdbcBForecasts(JdbcBDatabase db, InventoryRuntime inventory, Settings settings) {
        this.db = db;
        this.inventory = inventory;
        this.settings = settings;
    }

    private BigDecimal value(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    public List<Map<String, Object>> lines() {
        var now = db.now();
        var result = new ArrayList<Map<String, Object>>();
        var output =
                db.one(
                        "SELECT COALESCE(SUM(pl.qty),0) AS qty FROM production_log pl JOIN"
                            + " equipment e ON e.equipment_id=pl.equipment_id JOIN process p ON"
                            + " p.process_id=e.process_id JOIN line l ON l.line_id=p.line_id WHERE"
                            + " l.line_type='ASSEMBLY' AND e.deleted_at IS NULL AND"
                            + " p.process_seq=(SELECT MAX(p2.process_seq) FROM process p2 WHERE"
                            + " p2.line_id=p.line_id) AND pl.minute_at>=? AND pl.minute_at<?",
                        time(now.minusMinutes(10)),
                        time(now));
        BigDecimal hourly = value(output.get("qty")).multiply(BigDecimal.valueOf(6)),
                price = settings.decimal("UNIT_PRICE", BigDecimal.ZERO);
        for (var line : inventory.recalculate(now)) {
            BigDecimal net = line.consumptionPerMinute().subtract(line.productionPerMinute());
            Integer minutes =
                    net.signum() > 0
                            ? BigDecimal.valueOf(line.quantity())
                                    .divide(net, 0, RoundingMode.CEILING)
                                    .intValueExact()
                            : null;
            var stop = minutes == null ? null : now.plusMinutes(minutes);
            OffsetDateTime recovery = null;
            Long source = null, alarmId = null;
            BigDecimal loss = null;
            Integer margin = null;
            var fault =
                    db.one(
                            "SELECT a.alarm_id,a.equipment_id,a.occurred_at FROM alarm a JOIN"
                                + " equipment e ON e.equipment_id=a.equipment_id JOIN process p ON"
                                + " p.process_id=e.process_id WHERE p.line_id=? AND"
                                + " a.grade='CRITICAL' AND a.value_state='ABNORMAL' AND"
                                + " a.process_state<>'CLOSED' AND e.deleted_at IS NULL ORDER BY"
                                + " a.occurred_at LIMIT 1",
                            line.lineId());
            if (fault != null) {
                source = number(fault, "equipment_id");
                alarmId = number(fault, "alarm_id");
                var history =
                        db.rows(
                                "SELECT occurred_at,closed_at FROM alarm WHERE equipment_id=? AND"
                                        + " grade='CRITICAL' AND closed_at IS NOT NULL AND"
                                        + " process_result='EFFECTIVE'",
                                source);
                double average =
                        history.stream()
                                .mapToLong(
                                        a ->
                                                Math.max(
                                                        0,
                                                        Duration.between(
                                                                        dateTime(
                                                                                a.get(
                                                                                        "occurred_at")),
                                                                        dateTime(
                                                                                a.get("closed_at")))
                                                                .getSeconds()))
                                .average()
                                .orElse(-1);
                if (average >= 0) {
                    recovery = dateTime(fault.get("occurred_at")).plusSeconds((long) average);
                    if (stop != null) {
                        margin = Math.toIntExact(Duration.between(recovery, stop).toMinutes());
                        long seconds = Math.max(0, Duration.between(stop, recovery).getSeconds());
                        loss =
                                BigDecimal.valueOf(seconds)
                                        .multiply(hourly)
                                        .multiply(price)
                                        .divide(BigDecimal.valueOf(3600), 0, RoundingMode.HALF_UP);
                    }
                }
            }
            String stage =
                    minutes == null
                            ? "MAINTAIN"
                            : minutes <= settings.integer("STOCK_DANGER_MIN", 10)
                                    ? "DANGER"
                                    : minutes <= settings.integer("STOCK_LEVEL_MIN", 30)
                                            ? "CAUTION"
                                            : "MAINTAIN";
            result.add(
                    map(
                            "lineId",
                            line.lineId(),
                            "lineName",
                            line.lineName(),
                            "currentInventory",
                            line.quantity(),
                            "netConsumptionPerMinute",
                            net,
                            "remainingMinutes",
                            minutes,
                            "stage",
                            stage,
                            "assemblyStopExpectedAt",
                            stop,
                            "sourceEquipmentId",
                            source,
                            "activeAlarmId",
                            alarmId,
                            "recoveryExpectedAt",
                            recovery,
                            "marginMinutes",
                            margin,
                            "marginStatus",
                            margin == null ? null : margin >= 0 ? "AVAILABLE" : "SHORTAGE",
                            "hourlyProduction",
                            hourly,
                            "unitPrice",
                            price,
                            "expectedLossAmount",
                            loss));
        }
        return result;
    }

    public void capture(long alarmId) {
        for (var line : lines())
            if (Objects.equals(line.get("activeAlarmId"), alarmId)
                    && line.get("assemblyStopExpectedAt") != null
                    && line.get("recoveryExpectedAt") != null) {
                var alarm = db.one("SELECT occurred_at FROM alarm WHERE alarm_id=?", alarmId);
                var occurred = dateTime(alarm.get("occurred_at"));
                if (db.one("SELECT alarm_id FROM backend_alarm_impact WHERE alarm_id=?", alarmId)
                        == null)
                    db.update(
                            "INSERT INTO"
                                + " backend_alarm_impact(alarm_id,expected_stop_at,expected_downtime_minutes,hourly_output,unit_price)"
                                + " VALUES (?,?,?,?,?)",
                            alarmId,
                            time((OffsetDateTime) line.get("assemblyStopExpectedAt")),
                            Math.max(
                                    0,
                                    Duration.between(
                                                    occurred,
                                                    (OffsetDateTime) line.get("recoveryExpectedAt"))
                                            .toMinutes()),
                            line.get("hourlyProduction"),
                            line.get("unitPrice"));
            }
    }

    public void recovered(long alarmId, OffsetDateTime at) {
        db.update(
                "UPDATE backend_alarm_impact SET recovered_at=? WHERE alarm_id=? AND recovered_at"
                        + " IS NULL",
                time(at),
                alarmId);
    }

    public LossAvoidanceResponse avoidance() {
        var now = db.now();
        var today = now.toLocalDate();
        BigDecimal todayAmount = BigDecimal.ZERO,
                previous = BigDecimal.ZERO,
                monthly = BigDecimal.ZERO;
        int early = 0;
        var rows =
                db.rows(
                        "SELECT i.*,a.occurred_at,a.process_result FROM backend_alarm_impact i JOIN"
                            + " alarm a ON a.alarm_id=i.alarm_id WHERE i.recovered_at IS NOT NULL"
                            + " AND a.process_result='EFFECTIVE'");
        for (var r : rows) {
            var recovery = dateTime(r.get("recovered_at"));
            if (!recovery.isBefore(dateTime(r.get("expected_stop_at")))) continue;
            BigDecimal amount =
                    value(r.get("expected_downtime_minutes"))
                            .multiply(value(r.get("hourly_output")))
                            .multiply(value(r.get("unit_price")))
                            .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
            var date = dateTime(r.get("occurred_at")).toLocalDate();
            if (date.equals(today)) {
                todayAmount = todayAmount.add(amount);
                early++;
            }
            if (date.equals(today.minusDays(1))) previous = previous.add(amount);
            if (YearMonth.from(date).equals(YearMonth.from(today)) && !date.isAfter(today))
                monthly = monthly.add(amount);
        }
        int faults =
                db.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM alarm WHERE grade='CRITICAL' AND occurred_at>=? AND"
                                + " occurred_at<?",
                        Integer.class,
                        today.atStartOfDay(),
                        today.plusDays(1).atStartOfDay());
        return new LossAvoidanceResponse(
                true,
                new LossAvoidance(
                        todayAmount,
                        todayAmount.subtract(previous),
                        monthly,
                        faults,
                        early,
                        faults == 0
                                ? BigDecimal.ZERO
                                : BigDecimal.valueOf(100.0 * early / faults)));
    }
}
