package com.sfaasproject.stf.common.integration;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.operations.adapter.JdbcBDatabase;
import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.operations.port.V7Context;

import java.math.*;
import java.time.*;
import java.util.*;

/** A-side SQL adapter: equipment/measurement/settings/account metadata for B services. */
public class JdbcV7Context implements V7Context {
    private final JdbcBDatabase db;

    public JdbcV7Context(JdbcBDatabase db) {
        this.db = db;
    }

    private BigDecimal dec(Object v) {
        return v == null ? null : new BigDecimal(v.toString());
    }

    public void lockEquipment(long id) {
        db.rows("SELECT equipment_id FROM equipment WHERE equipment_id=? FOR UPDATE", id);
    }

    public void resetAlarmStreaks(long id) {
        db.update(
                "UPDATE measurement_latest SET out_streak=0,critical_streak=0,normal_streak=0 WHERE"
                    + " data_item_id=?",
                id);
    }

    public List<Line> lines() {
        return db.rows("SELECT * FROM line ORDER BY sort_order,line_id").stream()
                .map(
                        r ->
                                new Line(
                                        number(r, "line_id"),
                                        string(r, "name"),
                                        LineType.valueOf(string(r, "line_type")),
                                        (int) number(r, "sort_order"),
                                        r.get("deleted_at") != null))
                .toList();
    }

    public List<Equipment> equipment() {
        return db
                .rows(
                        "SELECT e.*,p.line_id,p.name AS"
                            + " process_name,p.process_seq,s.status,s.status_since FROM equipment e"
                            + " JOIN process p ON p.process_id=e.process_id LEFT JOIN"
                            + " equipment_state s ON s.equipment_id=e.equipment_id ORDER BY"
                            + " p.line_id,p.process_seq,e.equipment_id")
                .stream()
                .map(
                        r ->
                                new Equipment(
                                        number(r, "equipment_id"),
                                        string(r, "equip_code"),
                                        string(r, "name"),
                                        number(r, "line_id"),
                                        number(r, "process_id"),
                                        string(r, "process_name"),
                                        (int) number(r, "process_seq"),
                                        r.get("status") == null
                                                ? EquipmentStatus.COMMUNICATION_LOST
                                                : EquipmentStatus.valueOf(string(r, "status")),
                                        dateTime(
                                                r.get("status_since") == null
                                                        ? r.get("registered_at")
                                                        : r.get("status_since")),
                                        r.get("deleted_at") != null))
                .toList();
    }

    public List<Item> items() {
        return db
                .rows(
                        "SELECT d.*,m.measured_value,m.measured_at FROM data_item d LEFT JOIN"
                            + " measurement_latest m ON m.data_item_id=d.data_item_id ORDER BY"
                            + " d.sort_order,d.data_item_id")
                .stream()
                .map(
                        r ->
                                new Item(
                                        number(r, "data_item_id"),
                                        number(r, "equipment_id"),
                                        string(r, "item_code"),
                                        string(r, "name"),
                                        DataValueType.valueOf(string(r, "item_kind")),
                                        string(r, "item_role"),
                                        string(r, "unit"),
                                        "MEASUREMENT".equals(string(r, "item_kind"))
                                                ? (int) number(r, "decimal_places")
                                                : 0,
                                        dec(r.get("caution_limit")),
                                        dec(r.get("critical_limit")),
                                        string(r, "limit_direction"),
                                        r.get("alarm_state_value") == null
                                                ? null
                                                : (int) number(r, "alarm_state_value"),
                                        string(r, "alarm_state_grade"),
                                        dec(r.get("measured_value")),
                                        dateTime(r.get("measured_at")),
                                        r.get("deleted_at") != null))
                .toList();
    }

    public List<MeasurementPoint> points(long id, OffsetDateTime from, OffsetDateTime to) {
        return db
                .rows(
                        "SELECT measured_at,measured_value FROM measurement WHERE data_item_id=?"
                            + " AND measured_at>=? AND measured_at<? ORDER BY measured_at",
                        id,
                        time(from),
                        time(to))
                .stream()
                .map(
                        r ->
                                new MeasurementPoint(
                                        dateTime(r.get("measured_at")),
                                        dec(r.get("measured_value"))))
                .toList();
    }

    public List<StatusInterval> statuses(OffsetDateTime from, OffsetDateTime to) {
        return db
                .rows(
                        "SELECT * FROM equipment_status_history WHERE started_at<? AND (ended_at IS"
                            + " NULL OR ended_at>?)",
                        time(to),
                        time(from))
                .stream()
                .map(
                        r ->
                                new StatusInterval(
                                        number(r, "equipment_id"),
                                        EquipmentStatus.valueOf(string(r, "status")),
                                        dateTime(r.get("started_at")),
                                        dateTime(r.get("ended_at"))))
                .toList();
    }

    private String terminalFilter() {
        return "e.deleted_at IS NULL AND p.process_seq=(SELECT MAX(p2.process_seq) FROM process p2"
                   + " WHERE p2.line_id=p.line_id)";
    }

    public BigDecimal productionRate(long line, OffsetDateTime at, int window) {
        var r =
                db.one(
                        "SELECT SUM(pl.qty) AS qty FROM production_log pl JOIN equipment e ON"
                            + " e.equipment_id=pl.equipment_id JOIN process p ON"
                            + " p.process_id=e.process_id WHERE p.line_id=? AND "
                                + terminalFilter()
                                + " AND pl.minute_at>=? AND pl.minute_at<?",
                        line,
                        time(at.minusMinutes(window)),
                        time(at));
        return dec(r.get("qty")) == null
                ? BigDecimal.ZERO
                : dec(r.get("qty")).divide(BigDecimal.valueOf(window), 12, RoundingMode.HALF_UP);
    }

    public String counterQuality(long line, OffsetDateTime at, int window) {
        var rows =
                db.rows(
                        "SELECT m.measured_at,m.counter_status,m.counter_reset_at FROM equipment e"
                            + " JOIN process p ON p.process_id=e.process_id JOIN data_item d ON"
                            + " d.equipment_id=e.equipment_id AND d.item_role='PRODUCTION' AND"
                            + " d.deleted_at IS NULL LEFT JOIN measurement_latest m ON"
                            + " m.data_item_id=d.data_item_id WHERE p.line_id=? AND "
                                + terminalFilter(),
                        line);
        if (rows.isEmpty()) return "INSUFFICIENT_DATA";
        for (var r : rows) {
            var measured = dateTime(r.get("measured_at"));
            if (measured == null
                    || measured.isAfter(at)
                    || measured.isBefore(at.minusMinutes(window))) return "INSUFFICIENT_DATA";
            if ("COUNTER_INCONSISTENT".equals(r.get("counter_status")))
                return "COUNTER_INCONSISTENT";
            var reset = dateTime(r.get("counter_reset_at"));
            if (reset != null && !reset.isBefore(at.minusMinutes(window)))
                return "COUNTER_RESET_PENDING";
        }
        return "READY";
    }

    public int integer(String key, int fallback) {
        var r = db.one("SELECT setting_value FROM system_setting WHERE setting_key=?", key);
        return r == null ? fallback : Integer.parseInt(string(r, "setting_value"));
    }

    public BigDecimal decimal(String key, BigDecimal fallback) {
        var r = db.one("SELECT setting_value FROM system_setting WHERE setting_key=?", key);
        return r == null ? fallback : dec(r.get("setting_value"));
    }

    public UserBrief historicalUser(long id) {
        var r = db.one("SELECT account_id,name,role FROM account WHERE account_id=?", id);
        return r == null || r.get("role") == null
                ? null
                : new UserBrief(
                        number(r, "account_id"),
                        string(r, "name"),
                        Permission.valueOf(string(r, "role")));
    }

    public String accountName(long id) {
        var r = db.one("SELECT name FROM account WHERE account_id=?", id);
        return r == null ? "삭제된 계정" : string(r, "name");
    }

    public List<Long> connectedAccounts() {
        return db
                .rows(
                        "SELECT account_id FROM account WHERE deleted_at IS NULL AND"
                            + " telegram_chat_id IS NOT NULL ORDER BY account_id")
                .stream()
                .map(r -> number(r, "account_id"))
                .toList();
    }

    public List<Long> activeAccounts() {
        return db
                .rows(
                        "SELECT account_id FROM account WHERE deleted_at IS NULL AND"
                            + " approval_status='APPROVED' AND must_change_password=FALSE AND role"
                            + " IN ('ADMIN','USER') ORDER BY account_id")
                .stream()
                .map(r -> number(r, "account_id"))
                .toList();
    }
}
