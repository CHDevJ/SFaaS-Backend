package com.sfaasproject.stf.operations.adapter;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.alarm.service.AlarmGrade;
import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState;
import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.*;
import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.operations.port.BPorts.*;

import org.springframework.http.HttpStatus;

import java.time.*;
import java.util.*;

public class JdbcBAlarms implements Alarms {
    private final JdbcBDatabase db;

    public JdbcBAlarms(JdbcBDatabase db) {
        this.db = db;
    }

    @Override
    public Alarm lock(long id) {
        var r =
                db.one(
                        "SELECT a.*,p.line_id FROM alarm a JOIN equipment e ON"
                            + " e.equipment_id=a.equipment_id JOIN process p ON"
                            + " p.process_id=e.process_id WHERE a.alarm_id=? AND e.deleted_at IS"
                            + " NULL FOR UPDATE",
                        id);
        if (r == null) return null;
        var action =
                db.one(
                        "SELECT * FROM alarm_action WHERE alarm_id=? ORDER BY acted_at"
                                + " DESC,action_id DESC LIMIT 1",
                        id);
        String process = string(r, "process_state");
        if ("ACTION_TAKEN".equals(process)) process = "ACTION";
        boolean recovered = "RECOVERED".equals(string(r, "value_state"));
        EffectStatus effect =
                action == null || action.get("verify_started_at") == null
                        ? EffectStatus.WAITING_FOR_RECOVERY
                        : "EFFECTIVE".equals(string(action, "effect_result"))
                                ? EffectStatus.PASSED
                                : "RECURRED".equals(string(action, "effect_result"))
                                        ? EffectStatus.RECURRED
                                        : EffectStatus.IN_PROGRESS;
        var state =
                new AlarmWorkflowState(
                        ProcessStatus.valueOf(process),
                        recovered,
                        r.get("confirmed_by") == null ? null : number(r, "confirmed_by"),
                        r.get("assignee_id") == null ? null : number(r, "assignee_id"),
                        local(r.get("confirmed_at")),
                        local(r.get("acted_at")),
                        local(r.get("closed_at")),
                        r.get("process_result") == null
                                ? null
                                : Result.valueOf(string(r, "process_result")),
                        r.get("false_positive_reason") == null
                                ? null
                                : FalsePositiveReason.valueOf(string(r, "false_positive_reason")),
                        string(r, "false_positive_detail"),
                        effect,
                        action == null ? 0 : (int) number(action, "verify_target_count"),
                        action == null ? 0 : (int) number(action, "verify_passed_count"),
                        action == null ? null : local(action.get("verify_started_at")),
                        (int) number(r, "recurrence_count"));
        return new Alarm(
                id,
                number(r, "equipment_id"),
                number(r, "line_id"),
                number(r, "data_item_id"),
                AlarmGrade.valueOf(string(r, "grade")),
                dateTime(r.get("occurred_at")),
                state,
                number(r, "version"));
    }

    @Override
    public Long save(Alarm before, AlarmWorkflowState after, Mutation m) {
        Long actionId = null;
        if (m.type().equals("RECOVER"))
            db.update(
                    "UPDATE backend_alarm_impact SET recovered_at=? WHERE alarm_id=? AND"
                            + " recovered_at IS NULL",
                    time(m.at()),
                    before.id());
        int changed =
                db.update(
                        "UPDATE alarm SET"
                            + " process_state=?,value_state=?,confirmed_by=?,confirmed_at=?,assignee_id=?,acted_at=?,closed_at=?,process_result=?,false_positive_reason=?,false_positive_detail=?,recurrence_count=?,recovered_at=CASE"
                            + " WHEN ? THEN COALESCE(recovered_at,?) ELSE NULL"
                            + " END,version=version+1 WHERE alarm_id=? AND version=?",
                        after.processStatus() == ProcessStatus.ACTION
                                ? "ACTION_TAKEN"
                                : after.processStatus().name(),
                        after.valueRecovered() ? "RECOVERED" : "ABNORMAL",
                        after.acknowledgedBy(),
                        ts(after.acknowledgedAt()),
                        after.assigneeId(),
                        ts(after.actionedAt()),
                        ts(after.closedAt()),
                        after.result() == null ? null : after.result().name(),
                        after.falsePositiveReason() == null
                                ? null
                                : after.falsePositiveReason().name(),
                        after.falsePositiveDetail(),
                        after.recurrenceCount(),
                        after.valueRecovered(),
                        after.valueRecovered() ? time(m.at()) : null,
                        before.id(),
                        before.version());
        if (changed != 1)
            throw new DomainException(
                    HttpStatus.CONFLICT, "ALARM_STATE_CONFLICT", "알람이 변경되었습니다. 다시 조회해 주세요.");
        if (m.type().equals("ACTION")) {
            actionId =
                    db.insert(
                            "INSERT INTO"
                                + " alarm_action(alarm_id,cause,action_content,acted_by,acted_at,verify_target_count,verify_passed_count,verify_started_at)"
                                + " VALUES (?,?,?,?,?,?,?,?)",
                            before.id(),
                            m.cause(),
                            m.content(),
                            m.actorId(),
                            time(m.at()),
                            after.targetProductionCount(),
                            after.currentProductionCount(),
                            ts(after.verificationStartedAt()));
        } else {
            var action =
                    db.one(
                            "SELECT action_id FROM alarm_action WHERE alarm_id=? ORDER BY acted_at"
                                    + " DESC,action_id DESC LIMIT 1",
                            before.id());
            if (action != null)
                db.update(
                        "UPDATE alarm_action SET"
                            + " verify_passed_count=?,verify_started_at=?,verify_ended_at=?,effect_result=?"
                            + " WHERE action_id=?",
                        after.currentProductionCount(),
                        ts(after.verificationStartedAt()),
                        ts(after.closedAt()),
                        after.effectStatus() == EffectStatus.PASSED
                                ? "EFFECTIVE"
                                : after.effectStatus() == EffectStatus.RECURRED
                                        ? "RECURRED"
                                        : "PENDING",
                        number(action, "action_id"));
        }
        if (!m.type().equals("VERIFY_PROGRESS"))
            db.update(
                    "INSERT INTO alarm_log(alarm_id,event_type,actor_id,content,created_at) VALUES"
                            + " (?,?,?,?,?)",
                    before.id(),
                    m.type(),
                    m.actorId(),
                    m.content(),
                    time(m.at()));
        return actionId;
    }

    @Override
    public List<Long> unacknowledgedBefore(OffsetDateTime at) {
        return db
                .rows(
                        "SELECT alarm_id FROM alarm WHERE process_state='UNACKNOWLEDGED' AND"
                                + " renotified_at IS NULL AND occurred_at<=?",
                        time(at))
                .stream()
                .map(r -> number(r, "alarm_id"))
                .toList();
    }

    @Override
    public boolean markReminder(long id, OffsetDateTime at) {
        return db.update(
                        "UPDATE alarm SET renotified_at=? WHERE alarm_id=? AND renotified_at IS"
                                + " NULL AND process_state='UNACKNOWLEDGED'",
                        time(at),
                        id)
                == 1;
    }

    @Override
    public List<Long> verifyingInLine(long lineId) {
        return db
                .rows(
                        "SELECT a.alarm_id FROM alarm a JOIN equipment e ON"
                            + " e.equipment_id=a.equipment_id JOIN process p ON"
                            + " p.process_id=e.process_id WHERE p.line_id=? AND"
                            + " a.process_state='ACTION_TAKEN' AND a.value_state='RECOVERED' AND"
                            + " e.deleted_at IS NULL",
                        lineId)
                .stream()
                .map(r -> number(r, "alarm_id"))
                .toList();
    }

    @Override
    public int unacknowledgedCount() {
        return db.jdbc.queryForObject(
                "SELECT COUNT(*) FROM alarm a JOIN equipment e ON e.equipment_id=a.equipment_id"
                        + " WHERE a.process_state='UNACKNOWLEDGED' AND e.deleted_at IS NULL",
                Integer.class);
    }

    @Override
    public int activeCount() {
        return db.jdbc.queryForObject(
                "SELECT COUNT(*) FROM alarm a JOIN equipment e ON e.equipment_id=a.equipment_id"
                        + " WHERE a.process_state <> 'CLOSED' AND e.deleted_at IS NULL",
                Integer.class);
    }

    private static LocalDateTime local(Object v) {
        var d = dateTime(v);
        return d == null ? null : d.toLocalDateTime();
    }

    private static java.sql.Timestamp ts(LocalDateTime v) {
        return v == null ? null : java.sql.Timestamp.valueOf(v);
    }
}
