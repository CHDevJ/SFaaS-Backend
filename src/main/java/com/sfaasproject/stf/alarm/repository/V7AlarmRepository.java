package com.sfaasproject.stf.alarm.repository;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.alarm.service.AlarmLifecycleService.Action;
import com.sfaasproject.stf.operations.adapter.JdbcBDatabase;

import java.time.OffsetDateTime;
import java.util.*;

/** B-owned tables only. A metadata is supplied through V7Context. */
public class V7AlarmRepository {
    private final JdbcBDatabase db;

    public V7AlarmRepository(JdbcBDatabase db) {
        this.db = db;
    }

    public List<Map<String, Object>> alarms() {
        return db.rows("SELECT * FROM alarm ORDER BY occurred_at DESC,alarm_id DESC");
    }

    public Map<String, Object> find(long id) {
        return db.one("SELECT * FROM alarm WHERE alarm_id=?", id);
    }

    public Map<String, Object> lock(long id) {
        return db.one("SELECT * FROM alarm WHERE alarm_id=? FOR UPDATE", id);
    }

    public Map<String, Object> action(long id) {
        return db.one(
                "SELECT * FROM alarm_action WHERE alarm_id=? ORDER BY acted_at DESC,action_id DESC"
                    + " LIMIT 1",
                id);
    }

    public int activeCount() {
        return db.rows("SELECT alarm_id FROM alarm WHERE process_state='ACTIVE'").size();
    }

    public boolean complete(long id, long version, Action action) {
        if (db.update(
                        "UPDATE alarm SET"
                            + " process_state='CLOSED',acted_at=?,closed_at=?,version=version+1"
                            + " WHERE alarm_id=? AND process_state='ACTIVE' AND version=?",
                        time(action.closedAt()),
                        time(action.closedAt()),
                        id,
                        version)
                != 1) return false;
        db.update(
                "INSERT INTO"
                    + " alarm_action(alarm_id,equipment_text,cause,action_content,acted_by,acted_at)"
                    + " VALUES (?,?,?,?,?,?)",
                id,
                action.equipmentText(),
                action.cause(),
                action.actionContent(),
                action.handledBy().userId(),
                time(action.closedAt()));
        db.update(
                "INSERT INTO alarm_log(alarm_id,event_type,actor_id,content,created_at) VALUES"
                    + " (?,'CLOSE',?,?,?)",
                id,
                action.handledBy().userId(),
                action.actionContent(),
                time(action.closedAt()));
        return true;
    }

    public long create(
            long equipment,
            long item,
            String grade,
            String message,
            Object value,
            Object limit,
            OffsetDateTime at) {
        return db.insertReturning(
                "alarm_id",
                "INSERT INTO"
                    + " alarm(equipment_id,data_item_id,grade,message,trigger_value,limit_value,process_state,occurred_at)"
                    + " VALUES (?,?,?,?,?,?,'ACTIVE',?)",
                equipment,
                item,
                grade,
                message,
                value,
                limit,
                time(at));
    }

    public Map<String, Object> open(long item) {
        return db.one(
                "SELECT * FROM alarm WHERE data_item_id=? AND process_state='ACTIVE' FOR UPDATE",
                item);
    }

    public void value(long id, boolean recovered, OffsetDateTime at) {
        db.update(
                "UPDATE alarm SET value_state=?,recovered_at=?,version=version+1 WHERE alarm_id=?"
                    + " AND process_state='ACTIVE'",
                recovered ? "RECOVERED" : "ABNORMAL",
                recovered ? time(at) : null,
                id);
    }

    public void escalate(long id, OffsetDateTime at) {
        db.update(
                "UPDATE alarm SET grade='CRITICAL',grade_changed_at=?,version=version+1 WHERE"
                    + " alarm_id=? AND process_state='ACTIVE'",
                time(at),
                id);
    }
}
