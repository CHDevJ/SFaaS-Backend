package com.sfaasproject.stf.alarm.service;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;
import static com.sfaasproject.stf.operations.service.V7Support.*;

import com.sfaasproject.stf.alarm.repository.V7AlarmRepository;
import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.operations.event.*;
import com.sfaasproject.stf.operations.port.BPorts.UnitOfWork;
import com.sfaasproject.stf.operations.port.V7Context;
import com.sfaasproject.stf.operations.service.V7Support.Period;

import org.springframework.http.HttpStatus;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class V7AlarmService implements com.sfaasproject.stf.alarm.port.AlarmQuery {
    private final V7AlarmRepository repository;
    private final V7Context context;
    private final UnitOfWork tx;
    private final BEventPublisher events;
    private final Clock clock;
    private final AlarmLifecycleService rules;
    private final AtomicLong sequence = new AtomicLong();

    public V7AlarmService(
            V7AlarmRepository repository,
            V7Context context,
            UnitOfWork tx,
            BEventPublisher events,
            Clock clock,
            AlarmLifecycleService rules) {
        this.repository = repository;
        this.context = context;
        this.tx = tx;
        this.events = events;
        this.clock = clock;
        this.rules = rules;
    }

    public Reference find(long id) {
        var r = repository.find(id);
        return r == null
                ? null
                : new Reference(id, number(r, "equipment_id"), number(r, "data_item_id"));
    }

    public long activeCount(long equipment) {
        return repository.alarms().stream()
                .filter(
                        r ->
                                number(r, "equipment_id") == equipment
                                        && "ACTIVE".equals(r.get("process_state")))
                .count();
    }

    public List<Marker> markers(long equipment, OffsetDateTime from, OffsetDateTime to) {
        var p = period(from, to, now());
        return rows(null, equipment, null).stream()
                .filter(r -> in(dateTime(r.get("occurred_at")), p))
                .map(
                        r ->
                                new Marker(
                                        number(r, "alarm_id"),
                                        number(r, "data_item_id"),
                                        string(r, "grade"),
                                        string(r, "process_state"),
                                        dateTime(r.get("occurred_at")),
                                        dateTime(r.get("closed_at"))))
                .toList();
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public List<Map<String, Object>> rows(
            Long line,
            Long equipment,
            com.sfaasproject.stf.operations.dto.V7Contract.AlarmGrade grade) {
        id(line);
        id(equipment);
        var catalog =
                context.equipment().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(V7Context.Equipment::id, e -> e));
        return repository.alarms().stream()
                .filter(
                        a -> {
                            var e = catalog.get(number(a, "equipment_id"));
                            return e != null
                                    && (line == null || e.lineId() == line)
                                    && (equipment == null || e.id() == equipment)
                                    && (grade == null || grade.name().equals(a.get("grade")));
                        })
                .toList();
    }

    private Map<String, Object> row(long id) {
        id(id);
        return rows(null, null, null).stream()
                .filter(r -> number(r, "alarm_id") == id)
                .findFirst()
                .orElseThrow(() -> missing("ALARM_NOT_FOUND"));
    }

    public Map<String, Object> summary(Map<String, Object> r) {
        long equipment = number(r, "equipment_id"),
                item = number(r, "data_item_id"),
                id = number(r, "alarm_id");
        var e =
                context.equipment().stream()
                        .filter(x -> x.id() == equipment)
                        .findFirst()
                        .orElseThrow(() -> missing("EQUIPMENT_NOT_FOUND"));
        var d =
                context.items().stream()
                        .filter(x -> x.id() == item)
                        .findFirst()
                        .orElseThrow(() -> missing("DATA_ITEM_NOT_FOUND"));
        var line =
                context.lines().stream()
                        .filter(x -> x.id() == e.lineId())
                        .findFirst()
                        .orElseThrow();
        var action = repository.action(id);
        var occurred = dateTime(r.get("occurred_at"));
        var closed = dateTime(r.get("closed_at"));
        return com.sfaasproject.stf.operations.service.V7Support.map(
                "alarmId",
                id,
                "equipmentId",
                equipment,
                "equipmentCode",
                e.code(),
                "equipmentName",
                e.name(),
                "lineId",
                e.lineId(),
                "lineName",
                line.name(),
                "dataItemId",
                item,
                "dataItemName",
                d.name(),
                "alarmMessage",
                r.get("message"),
                "cause",
                action == null ? r.get("message") : action.get("cause"),
                "currentValue",
                d.latest(),
                "occurredValue",
                r.get("trigger_value"),
                "thresholdValue",
                r.get("limit_value"),
                "unit",
                d.unit(),
                "grade",
                r.get("grade"),
                "valueStatus",
                r.get("value_state"),
                "processStatus",
                r.get("process_state"),
                "occurredAt",
                occurred,
                "recoveredAt",
                dateTime(r.get("recovered_at")),
                "closedAt",
                closed,
                "elapsedSeconds",
                Math.max(0, Duration.between(occurred, now()).getSeconds()),
                "durationSeconds",
                closed == null
                        ? null
                        : Math.max(0, Duration.between(occurred, closed).getSeconds()),
                "actionContent",
                action == null ? null : action.get("action_content"),
                "cautionThreshold",
                d.kind() == DataValueType.MEASUREMENT ? d.caution() : null,
                "criticalThreshold",
                d.kind() == DataValueType.MEASUREMENT ? d.critical() : null,
                "thresholdDirection",
                d.kind() == DataValueType.MEASUREMENT ? d.direction() : null,
                "decimalPlaces",
                d.decimalPlaces(),
                "sparkline",
                context.points(item, now().minusMinutes(30), now().plusNanos(1)));
    }

    public AlarmPageResponse list(
            Long line,
            Long equipment,
            AlarmProcessStatus status,
            com.sfaasproject.stf.operations.dto.V7Contract.AlarmGrade grade,
            int page,
            int size) {
        page(page, size);
        var instant = now();
        var globalActive =
                rows(null, null, null).stream()
                        .filter(r -> "ACTIVE".equals(r.get("process_state")))
                        .toList();
        var all = rows(line, equipment, grade);
        var selected =
                all.stream()
                        .filter(r -> status == null || status.name().equals(r.get("process_state")))
                        .sorted(
                                Comparator.comparingInt(
                                                (Map<String, Object> r) ->
                                                        "ACTIVE".equals(r.get("process_state"))
                                                                ? 0
                                                                : 1)
                                        .thenComparing(
                                                r -> dateTime(r.get("occurred_at")),
                                                Comparator.reverseOrder())
                                        .thenComparing(
                                                r -> number(r, "alarm_id"),
                                                Comparator.reverseOrder()))
                        .toList();
        var today =
                instant.atZoneSameInstant(KST).toLocalDate().atStartOfDay(KST).toOffsetDateTime();
        var closedToday =
                all.stream()
                        .filter(
                                r ->
                                        r.get("closed_at") != null
                                                && !dateTime(r.get("closed_at")).isBefore(today)
                                                && !dateTime(r.get("closed_at")).isAfter(now()))
                        .toList();
        var durations =
                closedToday.stream()
                        .map(
                                r ->
                                        minutes(
                                                dateTime(r.get("occurred_at")),
                                                dateTime(r.get("closed_at"))))
                        .toList();
        var avg =
                durations.isEmpty()
                        ? null
                        : durations.stream()
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(
                                        BigDecimal.valueOf(durations.size()),
                                        12,
                                        RoundingMode.HALF_UP);
        return dto(
                com.sfaasproject.stf.operations.service.V7Support.map(
                        "success",
                        true,
                        "data",
                        com.sfaasproject.stf.operations.service.V7Support.map(
                                "activeCount",
                                globalActive.size(),
                                "activeCautionCount",
                                globalActive.stream()
                                        .filter(r -> "CAUTION".equals(r.get("grade")))
                                        .count(),
                                "activeCriticalCount",
                                globalActive.stream()
                                        .filter(r -> "CRITICAL".equals(r.get("grade")))
                                        .count(),
                                "yesterdayOccurredCount",
                                all.stream()
                                        .filter(
                                                r ->
                                                        in(
                                                                dateTime(r.get("occurred_at")),
                                                                new Period(
                                                                        today.minusDays(1), today)))
                                        .count(),
                                "todayOccurredCount",
                                all.stream()
                                        .filter(
                                                r ->
                                                        !dateTime(r.get("occurred_at"))
                                                                        .isBefore(today)
                                                                && !dateTime(r.get("occurred_at"))
                                                                        .isAfter(now()))
                                        .count(),
                                "todayClosedCount",
                                closedToday.size(),
                                "averageActionMinutes",
                                avg,
                                "items",
                                slice(selected, page, size).stream().map(this::summary).toList()),
                        "meta",
                        meta(page, size, selected.size())),
                AlarmPageResponse.class);
    }

    public RecentAlarmListResponse recent(
            Long line,
            Long equipment,
            com.sfaasproject.stf.operations.dto.V7Contract.AlarmGrade grade,
            int page,
            int size) {
        page(page, size);
        var to = now().atZoneSameInstant(KST).toOffsetDateTime();
        var from = to.minusHours(24);
        var selected =
                rows(line, equipment, grade).stream()
                        .filter(r -> in(dateTime(r.get("occurred_at")), new Period(from, to)))
                        .sorted(
                                Comparator.comparing(
                                                (Map<String, Object> r) ->
                                                        dateTime(r.get("occurred_at")))
                                        .thenComparingLong(r -> number(r, "alarm_id"))
                                        .reversed())
                        .toList();
        var hourly = new ArrayList<AlarmHourlyCount>();
        for (int hour = 0; hour < 24; hour++) {
            var start = from.plusHours(hour);
            var end = start.plusHours(1);
            var bucket =
                    selected.stream()
                            .filter(r -> in(dateTime(r.get("occurred_at")), new Period(start, end)))
                            .toList();
            hourly.add(
                    new AlarmHourlyCount(
                            start,
                            end,
                            (int)
                                    bucket.stream()
                                            .filter(r -> "CAUTION".equals(r.get("grade")))
                                            .count(),
                            (int)
                                    bucket.stream()
                                            .filter(r -> "CRITICAL".equals(r.get("grade")))
                                            .count()));
        }
        return new RecentAlarmListResponse(
                from,
                to,
                hourly,
                true,
                slice(selected, page, size).stream()
                        .map(r -> dto(summary(r), AlarmSummary.class))
                        .toList(),
                meta(page, size, selected.size()));
    }

    public AlarmDetailResponse detail(long id, OffsetDateTime expectedStop) {
        var r = row(id);
        var data = summary(r);
        var at = dateTime(r.get("occurred_at"));
        var end = at.plusMinutes(30);
        if (end.isAfter(now())) end = now().plusNanos(1);
        data.put("assemblyStopExpectedAt", expectedStop);
        data.put(
                "trendBeforeAndAfter",
                context.points(number(r, "data_item_id"), at.minusMinutes(30), end));
        data.put(
                "equipmentHistories",
                rows(null, number(r, "equipment_id"), null).stream()
                        .filter(a -> "CLOSED".equals(a.get("process_state")))
                        .sorted(
                                Comparator.comparing(
                                                (Map<String, Object> a) ->
                                                        dateTime(a.get("closed_at")),
                                                Comparator.reverseOrder())
                                        .thenComparing(
                                                a -> number(a, "alarm_id"),
                                                Comparator.reverseOrder()))
                        .limit(20)
                        .map(this::summary)
                        .toList());
        data.put("action", action(r));
        return dto(
                com.sfaasproject.stf.operations.service.V7Support.map(
                        "success", true, "data", data),
                AlarmDetailResponse.class);
    }

    public Map<String, Object> action(Map<String, Object> alarm) {
        var a = repository.action(number(alarm, "alarm_id"));
        if (a == null || !"CLOSED".equals(alarm.get("process_state"))) return null;
        var e =
                context.equipment().stream()
                        .filter(x -> x.id() == number(alarm, "equipment_id"))
                        .findFirst()
                        .orElseThrow();
        return com.sfaasproject.stf.operations.service.V7Support.map(
                "alarmId",
                alarm.get("alarm_id"),
                "processStatus",
                "CLOSED",
                "equipmentText",
                a.get("equipment_text") == null ? e.name() : a.get("equipment_text"),
                "cause",
                a.get("cause") == null ? alarm.get("message") : a.get("cause"),
                "actionContent",
                a.get("action_content"),
                "closedAt",
                dateTime(alarm.get("closed_at")),
                "handledBy",
                context.historicalUser(number(a, "acted_by")),
                "durationSeconds",
                Math.max(
                        0,
                        Duration.between(
                                        dateTime(alarm.get("occurred_at")),
                                        dateTime(alarm.get("closed_at")))
                                .getSeconds()));
    }

    public AlarmActionResponse complete(long id, AlarmActionRequest request, long actor) {
        id(id);
        return tx.execute(
                () -> {
                    var initial = repository.find(id);
                    if (initial == null) throw missing("ALARM_NOT_FOUND");
                    context.lockEquipment(number(initial, "equipment_id"));
                    context.resetAlarmStreaks(number(initial, "data_item_id"));
                    var r = repository.lock(id);
                    if (r == null) throw missing("ALARM_NOT_FOUND");
                    if (!"ACTIVE".equals(r.get("process_state")))
                        throw new DomainException(
                                HttpStatus.CONFLICT, "ALARM_STATE_CONFLICT", "이미 종료된 알람입니다.");
                    var user = context.historicalUser(actor);
                    if (user == null) throw missing("USER_NOT_FOUND");
                    var before =
                            new AlarmLifecycleService.Alarm(
                                    id,
                                    number(r, "equipment_id"),
                                    number(r, "data_item_id"),
                                    string(r, "message"),
                                    com.sfaasproject.stf.alarm.service.AlarmGrade.valueOf(
                                            string(r, "grade")),
                                    AlarmLifecycleService.ProcessStatus.ACTIVE,
                                    AlarmLifecycleService.ValueStatus.valueOf(
                                            string(r, "value_state")),
                                    dateTime(r.get("occurred_at")),
                                    dateTime(r.get("recovered_at")),
                                    null,
                                    number(r, "version"));
                    var after =
                            rules.complete(
                                    before,
                                    new AlarmLifecycleService.ActionRequest(
                                            request.equipmentText(),
                                            request.cause(),
                                            request.actionContent()),
                                    new AlarmLifecycleService.Actor(
                                            actor,
                                            user.name(),
                                            AlarmLifecycleService.Role.valueOf(
                                                    user.permission().name())),
                                    now());
                    if (!repository.complete(id, before.version(), after.action()))
                        throw new DomainException(
                                HttpStatus.CONFLICT, "ALARM_STATE_CONFLICT", "알람이 변경되었습니다.");
                    publish(id, "CLOSED", string(r, "grade"), "CLOSED", now());
                    return dto(
                            com.sfaasproject.stf.operations.service.V7Support.map(
                                    "success", true, "data", after.action()),
                            AlarmActionResponse.class);
                });
    }

    public com.sfaasproject.stf.measurement.repository.IngestionStore.OpenAlarm openForUpdate(
            long item) {
        var r = repository.open(item);
        return r == null
                ? null
                : new com.sfaasproject.stf.measurement.repository.IngestionStore.OpenAlarm(
                        number(r, "alarm_id"),
                        com.sfaasproject.stf.alarm.service.AlarmGrade.valueOf(string(r, "grade")),
                        "RECOVERED".equals(r.get("value_state")));
    }

    public boolean abnormal(long equipment, com.sfaasproject.stf.alarm.service.AlarmGrade grade) {
        return repository.alarms().stream()
                .anyMatch(
                        r ->
                                number(r, "equipment_id") == equipment
                                        && "ACTIVE".equals(r.get("process_state"))
                                        && "ABNORMAL".equals(r.get("value_state"))
                                        && grade.name().equals(r.get("grade")));
    }

    public void ingest(
            com.sfaasproject.stf.measurement.repository.IngestionStore.Equipment equipment,
            com.sfaasproject.stf.measurement.repository.IngestionStore.DataItem item,
            BigDecimal value,
            java.time.LocalDateTime measuredAt,
            com.sfaasproject.stf.measurement.repository.IngestionStore.OpenAlarm open,
            AlarmEvaluationResult result) {
        var at = measuredAt.atZone(KST).toOffsetDateTime();
        switch (result.transition()) {
            case OCCUR -> {
                long id =
                        repository.create(
                                equipment.id(),
                                item.id(),
                                result.eventGrade().name(),
                                item.name() + " 기준 이탈",
                                value,
                                result.matchedLimit(),
                                at);
                publish(id, "CREATED", result.eventGrade().name(), "ACTIVE", at);
            }
            case GRADE_UP -> {
                repository.escalate(open.id(), at);
                publish(open.id(), "GRADE_ESCALATED", "CRITICAL", "ACTIVE", at);
            }
            case RECOVER -> {
                repository.value(open.id(), true, at);
                publish(open.id(), "VALUE_RECOVERED", open.grade().name(), "ACTIVE", at);
            }
            case RECUR -> {
                repository.value(open.id(), false, at);
                if (result.eventGrade() == com.sfaasproject.stf.alarm.service.AlarmGrade.CRITICAL
                        && open.grade() != result.eventGrade()) {
                    repository.escalate(open.id(), at);
                    publish(open.id(), "GRADE_ESCALATED", "CRITICAL", "ACTIVE", at);
                }
            }
            case NONE -> {}
        }
    }

    public void publish(long id, String type, String grade, String process, OffsetDateTime at) {
        events.publish(
                new V7Events.AlarmCommitted(
                        BEvents.id(),
                        sequence.incrementAndGet(),
                        id,
                        type,
                        com.sfaasproject.stf.operations.dto.V7Contract.AlarmGrade.valueOf(grade),
                        AlarmProcessStatus.valueOf(process),
                        at));
        events.publish(
                new V7Events.CountsCommitted(
                        BEvents.id(), sequence.incrementAndGet(), repository.activeCount()));
    }
}
