package com.sfaasproject.stf.operations.adapter;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.alarm.service.*;
import com.sfaasproject.stf.equipment.service.EquipmentStatusDecisionService.Status;
import com.sfaasproject.stf.measurement.repository.IngestionStore;
import com.sfaasproject.stf.operations.event.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** B event bridge for the existing ingestion transaction; locks an equipment batch first. */
public class JdbcIngestionStore implements IngestionStore {
    private final JdbcBDatabase db;
    private final BEventPublisher events;
    private final com.sfaasproject.stf.operations.service.AlarmApplicationService commands;
    private final AtomicLong seq = new AtomicLong();

    public JdbcIngestionStore(
            JdbcBDatabase db,
            BEventPublisher events,
            com.sfaasproject.stf.operations.service.AlarmApplicationService commands) {
        this.db = db;
        this.events = events;
        this.commands = commands;
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
        var r =
                db.one(
                        "SELECT alarm_id,grade,value_state FROM alarm WHERE data_item_id=? AND"
                                + " process_state<>'CLOSED' FOR UPDATE",
                        id);
        return r == null
                ? null
                : new OpenAlarm(
                        number(r, "alarm_id"),
                        AlarmGrade.valueOf(string(r, "grade")),
                        "RECOVERED".equals(string(r, "value_state")));
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
        publishMeasurement(id, value, measuredAt);
    }

    private void publishMeasurement(long id, BigDecimal value, LocalDateTime at) {
        var item = db.one("SELECT equipment_id,item_kind FROM data_item WHERE data_item_id=?", id);
        events.publish(
                new BEvents.MeasurementChanged(
                        BEvents.id(),
                        seq.incrementAndGet(),
                        number(item, "equipment_id"),
                        id,
                        com.sfaasproject.stf.operations.dto.BContract.DataValueType.valueOf(
                                string(item, "item_kind")),
                        value,
                        at.atZone(db.clock.getZone()).toOffsetDateTime()));
    }

    @Override
    public void applyAlarm(
            Equipment equipment,
            DataItem item,
            BigDecimal value,
            LocalDateTime measuredAt,
            OpenAlarm open,
            AlarmEvaluationResult result) {
        var at = measuredAt.atZone(db.clock.getZone()).toOffsetDateTime();
        long id;
        switch (result.transition()) {
            case OCCUR -> {
                id =
                        db.insert(
                                "INSERT INTO"
                                    + " alarm(equipment_id,data_item_id,grade,message,trigger_value,limit_value,occurred_at)"
                                    + " VALUES (?,?,?,?,?,?,?)",
                                equipment.id(),
                                item.id(),
                                result.eventGrade().name(),
                                item.name() + " 기준 이탈",
                                value,
                                result.matchedLimit(),
                                Timestamp.valueOf(measuredAt));
                db.update(
                        "INSERT INTO alarm_log(alarm_id,event_type,content,created_at) VALUES"
                                + " (?,'OCCUR',?,?)",
                        id,
                        item.name() + " 기준 이탈",
                        Timestamp.valueOf(measuredAt));
                new JdbcBForecasts(db, new JdbcBInventory(db), new JdbcBUsersSettings(db))
                        .capture(id);
                events.publish(
                        new BEvents.AlarmChanged(
                                BEvents.id(),
                                seq.incrementAndGet(),
                                id,
                                "OCCUR",
                                com.sfaasproject.stf.operations.dto.BContract.AlarmGrade.valueOf(
                                        result.eventGrade().name()),
                                com.sfaasproject.stf.operations.dto.BContract.AlarmProcessStatus
                                        .UNACKNOWLEDGED,
                                at));
                int count =
                        db.jdbc.queryForObject(
                                "SELECT COUNT(*) FROM alarm WHERE process_state='UNACKNOWLEDGED'",
                                Integer.class);
                events.publish(
                        new BEvents.AlarmCounts(BEvents.id(), seq.incrementAndGet(), count, at));
            }
            case GRADE_UP -> {
                id = open.id();
                db.update(
                        "UPDATE alarm SET grade='CRITICAL',grade_changed_at=?,version=version+1"
                                + " WHERE alarm_id=?",
                        Timestamp.valueOf(measuredAt),
                        id);
                db.update(
                        "INSERT INTO alarm_log(alarm_id,event_type,content,created_at) VALUES"
                                + " (?,'GRADE_UP',?,?)",
                        id,
                        "경보로 등급 상향",
                        Timestamp.valueOf(measuredAt));
                var r = db.one("SELECT process_state FROM alarm WHERE alarm_id=?", id);
                String process = string(r, "process_state");
                if ("ACTION_TAKEN".equals(process)) process = "ACTION";
                events.publish(
                        new BEvents.AlarmChanged(
                                BEvents.id(),
                                seq.incrementAndGet(),
                                id,
                                "GRADE_UP",
                                com.sfaasproject.stf.operations.dto.BContract.AlarmGrade.CRITICAL,
                                com.sfaasproject.stf.operations.dto.BContract.AlarmProcessStatus
                                        .valueOf(process),
                                at));
            }
            case RECOVER -> commands.valueChanged(open.id(), true, at);
            case RECUR -> commands.valueChanged(open.id(), false, at);
            default -> {}
        }
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
        var r =
                db.one(
                        "SELECT p.line_id,p.process_seq,(SELECT MAX(p2.process_seq) FROM process p2"
                                + " WHERE p2.line_id=p.line_id) AS end_seq FROM equipment e JOIN"
                                + " process p ON p.process_id=e.process_id WHERE e.equipment_id=?",
                        equipmentId);
        events.publish(
                new BEvents.ProductionRecorded(
                        BEvents.id(),
                        equipmentId,
                        number(r, "line_id"),
                        quantity,
                        number(r, "process_seq") == number(r, "end_seq"),
                        minuteAt.atZone(db.clock.getZone()).toOffsetDateTime()));
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
        return db.one(
                        "SELECT alarm_id FROM alarm WHERE equipment_id=? AND grade=? AND"
                                + " process_state<>'CLOSED' AND value_state='ABNORMAL' LIMIT 1",
                        id,
                        grade.name())
                != null;
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
                    Timestamp.valueOf(received));
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
                    Timestamp.valueOf(received),
                    id);
        } else
            db.update(
                    "UPDATE equipment_state SET last_received_at=? WHERE equipment_id=?",
                    Timestamp.valueOf(received),
                    id);
        if (previous == null || !status.name().equals(string(previous, "status"))) {
            db.update(
                    "INSERT INTO equipment_status_history(equipment_id,status,started_at) VALUES"
                            + " (?,?,?)",
                    id,
                    status.name(),
                    Timestamp.valueOf(at));
            events.publish(
                    new BEvents.EquipmentChanged(
                            BEvents.id(),
                            seq.incrementAndGet(),
                            id,
                            com.sfaasproject.stf.operations.dto.BContract.EquipmentStatus.valueOf(
                                    status.name()),
                            at.atZone(db.clock.getZone()).toOffsetDateTime(),
                            received.atZone(db.clock.getZone()).toOffsetDateTime()));
        }
    }
}
