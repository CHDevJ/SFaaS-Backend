package com.sfaasproject.stf.measurement.repository;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.alarm.service.*;
import com.sfaasproject.stf.equipment.service.EquipmentStatusDecisionService.Status;
import com.sfaasproject.stf.operations.adapter.JdbcBDatabase;
import com.sfaasproject.stf.operations.event.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** B event bridge for the existing ingestion transaction; locks an equipment batch first. */
public class JdbcV7IngestionStore implements IngestionStore {
    private final JdbcBDatabase db;
    private final BEventPublisher events;
    private final com.sfaasproject.stf.alarm.service.V7AlarmService commands;
    private final String inventorySource;
    private final AtomicLong seq = new AtomicLong();

    public JdbcV7IngestionStore(
            JdbcBDatabase db,
            BEventPublisher events,
            com.sfaasproject.stf.alarm.service.V7AlarmService commands,
            String inventorySource) {
        this.db = db;
        this.events = events;
        this.commands = commands;
        this.inventorySource = inventorySource;
    }

    private BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }

    @Override
    public Equipment findActiveEquipment(String code) {
        var r =
                db.one(
                        "SELECT equipment_id,equip_code FROM equipment WHERE equip_code=? AND"
                                + " deleted_at IS NULL FOR UPDATE",
                        code);
        return r == null ? null : new Equipment(number(r, "equipment_id"), string(r, "equip_code"));
    }

    @Override
    public List<DataItem> findActiveItems(long id) {
        return db
                .rows("SELECT * FROM data_item WHERE equipment_id=? AND deleted_at IS NULL", id)
                .stream()
                .map(
                        r ->
                                new DataItem(
                                        number(r, "data_item_id"),
                                        id,
                                        string(r, "item_code"),
                                        string(r, "name"),
                                        string(r, "item_kind"),
                                        string(r, "item_role"),
                                        string(r, "limit_direction"),
                                        decimal(r.get("caution_limit")),
                                        decimal(r.get("critical_limit")),
                                        r.get("alarm_state_value") == null
                                                ? null
                                                : (int) number(r, "alarm_state_value"),
                                        r.get("alarm_state_grade") == null
                                                ? null
                                                : AlarmGrade.valueOf(
                                                        string(r, "alarm_state_grade")),
                                        r.get("reset_time") == null
                                                ? null
                                                : ((Time) r.get("reset_time")).toLocalTime()))
                .toList();
    }

    @Override
    public Latest findLatestForUpdate(long id) {
        var r = db.one("SELECT * FROM measurement_latest WHERE data_item_id=? FOR UPDATE", id);
        return r == null
                ? null
                : new Latest(
                        decimal(r.get("measured_value")),
                        dateTime(r.get("measured_at")).toLocalDateTime(),
                        (int) number(r, "out_streak"),
                        (int) number(r, "critical_streak"),
                        (int) number(r, "normal_streak"));
    }

    @Override
    public OpenAlarm findOpenAlarmForUpdate(long id) {
        return commands.openForUpdate(id);
    }

    @Override
    public boolean measurementExists(long id, LocalDateTime at) {
        return db.one(
                        "SELECT data_item_id FROM measurement WHERE data_item_id=? AND"
                                + " measured_at=?",
                        id,
                        Timestamp.valueOf(at))
                != null;
    }

    @Override
    public void insertMeasurement(
            long id, LocalDateTime measuredAt, BigDecimal value, LocalDateTime receivedAt) {
        db.update(
                "INSERT INTO measurement(data_item_id,measured_at,measured_value) VALUES (?,?,?)",
                id,
                Timestamp.valueOf(measuredAt),
                value);
    }

    @Override
    public void saveLatest(
            long id,
            BigDecimal value,
            LocalDateTime measuredAt,
            LocalDateTime receivedAt,
            String grade,
            int out,
            int critical,
            int normal) {
        var previous =
                db.one(
                        "SELECT measured_value,measured_at FROM measurement_latest WHERE"
                            + " data_item_id=? FOR UPDATE",
                        id);
        var definition =
                db.one("SELECT item_kind,reset_time FROM data_item WHERE data_item_id=?", id);
        boolean counter = "CUMULATIVE".equals(definition.get("item_kind"));
        boolean reset =
                counter
                        && previous != null
                        && value.compareTo(decimal(previous.get("measured_value"))) < 0;
        if (counter && previous != null && definition.get("reset_time") != null) {
            var resetTime = ((java.sql.Time) definition.get("reset_time")).toLocalTime();
            var before = dateTime(previous.get("measured_at")).toLocalDateTime();
            var boundary = before.toLocalDate().atTime(resetTime);
            if (!boundary.isAfter(before)) boundary = boundary.plusDays(1);
            reset |= !boundary.isAfter(measuredAt);
        }
        int updated =
                db.update(
                        "UPDATE measurement_latest SET"
                            + " measured_value=?,measured_at=?,received_at=?,value_grade=?,out_streak=?,critical_streak=?,normal_streak=?"
                            + " WHERE data_item_id=?",
                        value,
                        Timestamp.valueOf(measuredAt),
                        Timestamp.valueOf(receivedAt),
                        grade,
                        out,
                        critical,
                        normal,
                        id);
        if (updated == 0)
            db.update(
                    "INSERT INTO"
                        + " measurement_latest(data_item_id,measured_value,measured_at,received_at,value_grade,out_streak,critical_streak,normal_streak)"
                        + " VALUES (?,?,?,?,?,?,?,?)",
                    id,
                    value,
                    Timestamp.valueOf(measuredAt),
                    Timestamp.valueOf(receivedAt),
                    grade,
                    out,
                    critical,
                    normal);
        if (counter) {
            if (reset)
                db.update(
                        "UPDATE measurement_latest SET"
                            + " counter_status='COUNTER_RESET_PENDING',counter_epoch=counter_epoch+1,counter_reset_at=?"
                            + " WHERE data_item_id=?",
                        Timestamp.valueOf(measuredAt),
                        id);
            else
                db.update(
                        "UPDATE measurement_latest SET counter_status='READY' WHERE data_item_id=?",
                        id);
        }
        publishMeasurement(id, value, measuredAt);
    }

    private void publishMeasurement(long id, BigDecimal value, LocalDateTime at) {
        var item = db.one("SELECT equipment_id,item_kind FROM data_item WHERE data_item_id=?", id);
        events.publish(
                new V7Events.MeasurementCommitted(
                        BEvents.id(),
                        seq.incrementAndGet(),
                        number(item, "equipment_id"),
                        id,
                        com.sfaasproject.stf.operations.dto.V7Contract.DataValueType.valueOf(
                                string(item, "item_kind")),
                        value,
                        at.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime()));
    }

    @Override
    public void applyAlarm(
            Equipment equipment,
            DataItem item,
            BigDecimal value,
            LocalDateTime measuredAt,
            OpenAlarm open,
            AlarmEvaluationResult result) {
        commands.ingest(equipment, item, value, measuredAt, open, result);
    }

    @Override
    public void addProduction(long equipmentId, LocalDateTime minuteAt, int quantity) {
        if (db.update(
                        "UPDATE production_log SET qty=qty+? WHERE equipment_id=? AND minute_at=?",
                        quantity,
                        equipmentId,
                        Timestamp.valueOf(minuteAt))
                == 0)
            db.update(
                    "INSERT INTO production_log(equipment_id,minute_at,qty) VALUES (?,?,?)",
                    equipmentId,
                    Timestamp.valueOf(minuteAt),
                    quantity);

        if (!"DEMO_PRODUCTION".equals(inventorySource)) return;
        var equipment =
                db.one(
                        "SELECT p.line_id,p.process_seq,l.line_type FROM equipment e JOIN process p"
                            + " ON p.process_id=e.process_id JOIN line l ON l.line_id=p.line_id"
                            + " WHERE e.equipment_id=? AND e.deleted_at IS NULL AND l.deleted_at IS"
                            + " NULL",
                        equipmentId);
        var terminal =
                db.one(
                        "SELECT MAX(process_seq) AS last_seq FROM process WHERE line_id=?",
                        equipment.get("line_id"));
        if (number(equipment, "process_seq") != number(terminal, "last_seq")) return;
        if ("ASSEMBLY".equals(equipment.get("line_type"))) {
            for (var line :
                    db.rows(
                            "SELECT line_id FROM line WHERE line_type='PART' AND deleted_at IS NULL"
                                + " ORDER BY line_id"))
                adjust(number(line, "line_id"), -quantity);
        } else adjust(number(equipment, "line_id"), quantity);
    }

    private void adjust(long line, long delta) {
        db.rows("SELECT line_id FROM line WHERE line_id=? FOR UPDATE", line);
        var previous =
                db.one(
                        "SELECT qty FROM stock_history WHERE line_id=? AND recorded_at<=? ORDER BY"
                            + " recorded_at DESC LIMIT 1",
                        line,
                        time(db.now()));
        if (previous == null)
            return; // No verified stock baseline: never invent the missing quantity.
        long next = Math.max(0, Math.addExact(number(previous, "qty"), delta));
        var at = time(db.now().withNano(0));
        if (db.update(
                        "UPDATE stock_history SET qty=? WHERE line_id=? AND recorded_at=?",
                        next,
                        line,
                        at)
                == 0)
            db.update(
                    "INSERT INTO stock_history(line_id,recorded_at,qty) VALUES (?,?,?)",
                    line,
                    at,
                    next);
    }

    @Override
    public Boolean findRunSignal(long id) {
        var r =
                db.one(
                        "SELECT ml.measured_value FROM data_item d JOIN measurement_latest ml ON"
                                + " ml.data_item_id=d.data_item_id WHERE d.equipment_id=? AND"
                                + " d.item_role='RUN_SIGNAL' AND d.deleted_at IS NULL",
                        id);
        return r == null ? null : decimal(r.get("measured_value")).compareTo(BigDecimal.ONE) == 0;
    }

    @Override
    public boolean hasAbnormalOpenAlarmGrade(long id, AlarmGrade grade) {
        return commands.abnormal(id, grade);
    }

    @Override
    public void saveEquipmentStatus(
            long id, Status status, LocalDateTime at, LocalDateTime received) {
        var previous =
                db.one("SELECT status FROM equipment_state WHERE equipment_id=? FOR UPDATE", id);
        if (previous == null) {
            db.update(
                    "INSERT INTO equipment_state(equipment_id,status,status_since,last_received_at)"
                            + " VALUES (?,?,?,?)",
                    id,
                    status.name(),
                    Timestamp.valueOf(at),
                    received == null ? null : Timestamp.valueOf(received));
        } else if (!status.name().equals(string(previous, "status"))) {
            db.update(
                    "UPDATE equipment_status_history SET ended_at=? WHERE equipment_id=? AND"
                            + " ended_at IS NULL",
                    Timestamp.valueOf(at),
                    id);
            db.update(
                    "UPDATE equipment_state SET status=?,status_since=?,last_received_at=? WHERE"
                            + " equipment_id=?",
                    status.name(),
                    Timestamp.valueOf(at),
                    received == null ? null : Timestamp.valueOf(received),
                    id);
        } else
            db.update(
                    "UPDATE equipment_state SET last_received_at=? WHERE equipment_id=?",
                    received == null ? null : Timestamp.valueOf(received),
                    id);
        if (previous == null || !status.name().equals(string(previous, "status"))) {
            db.update(
                    "INSERT INTO equipment_status_history(equipment_id,status,started_at) VALUES"
                            + " (?,?,?)",
                    id,
                    status.name(),
                    Timestamp.valueOf(at));
            events.publish(
                    new V7Events.EquipmentCommitted(
                            BEvents.id(),
                            seq.incrementAndGet(),
                            id,
                            com.sfaasproject.stf.operations.dto.V7Contract.EquipmentStatus.valueOf(
                                    status.name()),
                            at.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                            at.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime()));
        }
    }
}
