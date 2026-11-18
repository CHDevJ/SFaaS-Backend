package com.sfaasproject.stf.statistics.service;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;
import static com.sfaasproject.stf.operations.service.V7Support.*;

import com.sfaasproject.stf.alarm.service.V7AlarmService;
import com.sfaasproject.stf.operations.adapter.JdbcBDatabase;
import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.operations.port.V7Context;
import com.sfaasproject.stf.operations.service.V7Support.Period;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public class V7StatisticsService {
    private final V7Context context;
    private final JdbcBDatabase db;
    private final V7AlarmService alarms;
    private final ClosedAlarmStatisticsService metrics;
    private final StopCauseStatisticsService causes;
    private final ActualLossCalculator actualLoss;

    public V7StatisticsService(
            V7Context context,
            JdbcBDatabase db,
            V7AlarmService alarms,
            ClosedAlarmStatisticsService metrics,
            StopCauseStatisticsService causes,
            ActualLossCalculator actualLoss) {
        this.context = context;
        this.db = db;
        this.alarms = alarms;
        this.metrics = metrics;
        this.causes = causes;
        this.actualLoss = actualLoss;
    }

    private List<Map<String, Object>> selected(OffsetDateTime from, OffsetDateTime to, Long line) {
        id(line);
        var p = period(from, to, db.now());
        return alarms.rows(line, null, null).stream()
                .filter(r -> in(dateTime(r.get("occurred_at")), p))
                .toList();
    }

    private List<ClosedAlarmStatisticsService.Alarm> samples(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(
                        r ->
                                new ClosedAlarmStatisticsService.Alarm(
                                        number(r, "alarm_id"),
                                        number(r, "equipment_id"),
                                        com.sfaasproject.stf.alarm.service.AlarmGrade.valueOf(
                                                string(r, "grade")),
                                        com.sfaasproject.stf.alarm.service.AlarmLifecycleService
                                                .ProcessStatus.valueOf(string(r, "process_state")),
                                        dateTime(r.get("occurred_at")),
                                        dateTime(r.get("closed_at"))))
                .toList();
    }

    public AlarmResponseStatisticsResponse response(
            OffsetDateTime from, OffsetDateTime to, Long line) {
        var p = period(from, to, db.now());
        var result = metrics.response(samples(selected(p.from(), p.to(), line)), p.from(), p.to());
        var equipmentName =
                context.equipment().stream()
                        .filter(e -> Objects.equals(e.id(), result.longestActionEquipmentId()))
                        .map(V7Context.Equipment::name)
                        .findFirst()
                        .orElse(null);
        return new AlarmResponseStatisticsResponse(
                true,
                new AlarmResponseStatistics(
                        result.activeCautionCount(),
                        result.activeCriticalCount(),
                        result.longestActionEquipmentId(),
                        equipmentName,
                        result.totalCount(),
                        result.cautionCount(),
                        result.criticalCount(),
                        result.activeCount(),
                        result.averageActionMinutes(),
                        result.longestActionMinutes()));
    }

    public DailyAlarmStatisticsResponse daily(OffsetDateTime from, OffsetDateTime to, Long line) {
        var p = period(from, to, db.now());
        var rows = selected(p.from(), p.to(), line);
        var data = new ArrayList<DailyAlarmCount>();
        for (LocalDate d = p.from().atZoneSameInstant(KST).toLocalDate();
                !d.isAfter(p.to().minusNanos(1).atZoneSameInstant(KST).toLocalDate());
                d = d.plusDays(1)) {
            var day = d;
            var values =
                    rows.stream()
                            .filter(
                                    r ->
                                            dateTime(r.get("occurred_at"))
                                                    .atZoneSameInstant(KST)
                                                    .toLocalDate()
                                                    .equals(day))
                            .toList();
            int critical =
                    (int) values.stream().filter(r -> "CRITICAL".equals(r.get("grade"))).count();
            data.add(new DailyAlarmCount(day, critical, values.size() - critical));
        }
        return new DailyAlarmStatisticsResponse(true, data);
    }

    public AlarmHistoryPageResponse histories(
            OffsetDateTime from,
            OffsetDateTime to,
            Long line,
            Long equipment,
            AlarmGrade grade,
            int page,
            int size) {
        var p = period(from, to, db.now());
        var rows =
                alarms.rows(line, equipment, grade).stream()
                        .filter(
                                r ->
                                        "CLOSED".equals(r.get("process_state"))
                                                && in(dateTime(r.get("occurred_at")), p))
                        .toList();
        return dto(
                com.sfaasproject.stf.operations.service.V7Support.map(
                        "success",
                        true,
                        "data",
                        slice(rows, page, size).stream().map(alarms::summary).toList(),
                        "meta",
                        meta(page, size, rows.size())),
                AlarmHistoryPageResponse.class);
    }

    public FaultHistoryResponse faultHistory(
            long equipment, OffsetDateTime from, OffsetDateTime to) {
        id(equipment);
        if (context.equipment().stream().noneMatch(e -> e.id() == equipment))
            throw missing("EQUIPMENT_NOT_FOUND");
        var p = period(from, to, db.now());
        var rows =
                alarms.rows(null, equipment, AlarmGrade.CRITICAL).stream()
                        .filter(
                                r ->
                                        "CLOSED".equals(r.get("process_state"))
                                                && in(dateTime(r.get("occurred_at")), p))
                        .toList();
        var m = metrics.reliability(equipment, samples(rows), p.from(), p.to());
        var items =
                rows.stream()
                        .map(
                                r -> {
                                    var a = alarms.action(r);
                                    return com.sfaasproject.stf.operations.service.V7Support.map(
                                            "alarmId",
                                            r.get("alarm_id"),
                                            "occurredAt",
                                            dateTime(r.get("occurred_at")),
                                            "closedAt",
                                            dateTime(r.get("closed_at")),
                                            "cause",
                                            a == null ? r.get("message") : a.get("cause"),
                                            "actionContent",
                                            a == null ? "" : a.get("actionContent"),
                                            "durationSeconds",
                                            Duration.between(
                                                            dateTime(r.get("occurred_at")),
                                                            dateTime(r.get("closed_at")))
                                                    .getSeconds(),
                                            "handledBy",
                                            a == null ? null : a.get("handledBy"));
                                })
                        .toList();
        return dto(
                com.sfaasproject.stf.operations.service.V7Support.map(
                        "success",
                        true,
                        "data",
                        com.sfaasproject.stf.operations.service.V7Support.map(
                                "monthlyFaultCount",
                                m.faultCount(),
                                "averageRepairMinutes",
                                m.mttrMinutes(),
                                "averageFailureIntervalMinutes",
                                m.mtbfMinutes(),
                                "items",
                                items)),
                FaultHistoryResponse.class);
    }

    private List<V7Context.Equipment> equipment(Long line) {
        id(line);
        var activeLines =
                context.lines().stream().filter(l -> !l.deleted()).map(V7Context.Line::id).toList();
        return context.equipment().stream()
                .filter(
                        e ->
                                !e.deleted()
                                        && activeLines.contains(e.lineId())
                                        && (line == null || e.lineId() == line))
                .toList();
    }

    private Map<EquipmentStatus, BigDecimal> durations(
            long equipment, Period p, List<V7Context.StatusInterval> intervals) {
        var rows = intervals.stream().filter(i -> i.equipmentId() == equipment).toList();
        var boundaries =
                new TreeSet<OffsetDateTime>(Comparator.comparing(OffsetDateTime::toInstant));
        boundaries.add(p.from());
        boundaries.add(p.to());
        for (var i : rows) {
            if (i.startedAt().isAfter(p.from()) && i.startedAt().isBefore(p.to()))
                boundaries.add(i.startedAt());
            var end = i.endedAt() == null ? db.now() : i.endedAt();
            if (end.isAfter(p.from()) && end.isBefore(p.to())) boundaries.add(end);
        }
        var totals = new EnumMap<EquipmentStatus, BigDecimal>(EquipmentStatus.class);
        for (var s : EquipmentStatus.values()) totals.put(s, BigDecimal.ZERO);
        var times = new ArrayList<>(boundaries);
        for (int n = 1; n < times.size(); n++) {
            var start = times.get(n - 1);
            var end = times.get(n);
            if (start.isAfter(db.now())) continue;
            var winner =
                    rows.stream()
                            .filter(
                                    i ->
                                            !i.startedAt().isAfter(start)
                                                    && (i.endedAt() == null
                                                            ? !db.now().isBefore(end)
                                                            : !i.endedAt().isBefore(end)))
                            .min(
                                    Comparator.comparingInt(
                                            (V7Context.StatusInterval i) ->
                                                    switch (i.status()) {
                                                        case COMMUNICATION_LOST -> 0;
                                                        case FAULT -> 1;
                                                        case CAUTION -> 2;
                                                        case IDLE -> 3;
                                                        case RUNNING -> 4;
                                                    }))
                            .orElse(null);
            if (winner != null) totals.merge(winner.status(), minutes(start, end), BigDecimal::add);
        }
        return totals;
    }

    public AvailabilityStatisticsResponse availability(
            OffsetDateTime from, OffsetDateTime to, Long line) {
        var p = period(from, to, db.now());
        var equipment = equipment(line);
        var intervals = context.statuses(p.from(), p.to());
        var planned = minutes(p.from(), p.to());
        var data = new ArrayList<Map<String, Object>>();
        var running = new HashMap<Long, BigDecimal>();
        for (var e : equipment) {
            var value = durations(e.id(), p, intervals).get(EquipmentStatus.RUNNING);
            running.put(e.id(), value);
            data.add(availability("EQUIPMENT", e.id(), e.name(), value, planned));
        }
        for (var l :
                context.lines().stream()
                        .filter(l -> !l.deleted() && (line == null || l.id() == line))
                        .toList()) {
            var group = equipment.stream().filter(e -> e.lineId() == l.id()).toList();
            var value =
                    group.stream()
                            .map(e -> running.get(e.id()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            var linePlanned = planned.multiply(BigDecimal.valueOf(group.size()));
            data.add(availability("LINE", l.id(), l.name(), value, linePlanned));
        }
        return dto(
                com.sfaasproject.stf.operations.service.V7Support.map(
                        "success", true, "data", data),
                AvailabilityStatisticsResponse.class);
    }

    private Map<String, Object> availability(
            String type, long id, String name, BigDecimal running, BigDecimal planned) {
        var rate =
                planned.signum() == 0
                        ? BigDecimal.ZERO
                        : running.multiply(BigDecimal.valueOf(100))
                                .divide(planned, 12, RoundingMode.HALF_UP)
                                .min(BigDecimal.valueOf(100));
        return com.sfaasproject.stf.operations.service.V7Support.map(
                "targetType",
                type,
                "targetId",
                id,
                "targetName",
                name,
                "availabilityRate",
                rate,
                "operatingMinutes",
                running,
                "plannedMinutes",
                planned);
    }

    public DowntimeStatisticsResponse downtime(OffsetDateTime from, OffsetDateTime to, Long line) {
        var p = period(from, to, db.now());
        var intervals = context.statuses(p.from(), p.to());
        return dto(
                com.sfaasproject.stf.operations.service.V7Support.map(
                        "success",
                        true,
                        "data",
                        equipment(line).stream()
                                .map(
                                        e -> {
                                            var d = durations(e.id(), p, intervals);
                                            return com.sfaasproject.stf.operations.service.V7Support
                                                    .map(
                                                            "equipmentId",
                                                            e.id(),
                                                            "equipmentName",
                                                            e.name(),
                                                            "faultMinutes",
                                                            d.get(EquipmentStatus.FAULT),
                                                            "idleMinutes",
                                                            d.get(EquipmentStatus.IDLE),
                                                            "communicationLostMinutes",
                                                            d.get(
                                                                    EquipmentStatus
                                                                            .COMMUNICATION_LOST));
                                        })
                                .toList()),
                DowntimeStatisticsResponse.class);
    }

    public CriticalEquipmentResponse critical(OffsetDateTime from, OffsetDateTime to, Long line) {
        var p = period(from, to, db.now());
        var rows =
                alarms.rows(line, null, AlarmGrade.CRITICAL).stream()
                        .filter(
                                r ->
                                        in(dateTime(r.get("occurred_at")), p)
                                                || in(dateTime(r.get("grade_changed_at")), p))
                        .toList();
        var counts =
                rows.stream()
                        .collect(
                                Collectors.groupingBy(
                                        r -> number(r, "equipment_id"), Collectors.counting()));
        var equipment =
                context.equipment().stream()
                        .collect(Collectors.toMap(V7Context.Equipment::id, e -> e));
        return dto(
                com.sfaasproject.stf.operations.service.V7Support.map(
                        "success",
                        true,
                        "data",
                        counts.entrySet().stream()
                                .sorted(
                                        Map.Entry.<Long, Long>comparingByValue()
                                                .reversed()
                                                .thenComparing(Map.Entry.comparingByKey()))
                                .map(
                                        e ->
                                                com.sfaasproject.stf.operations.service.V7Support
                                                        .map(
                                                                "equipmentId",
                                                                e.getKey(),
                                                                "equipmentName",
                                                                equipment.get(e.getKey()).name(),
                                                                "criticalCount",
                                                                e.getValue()))
                                .toList()),
                CriticalEquipmentResponse.class);
    }

    public StopCauseResponse stopCauses(OffsetDateTime from, OffsetDateTime to, Long line) {
        var p = period(from, to, db.now());
        var ids = equipment(line).stream().map(V7Context.Equipment::id).toList();
        var stops = new ArrayList<StopCauseStatisticsService.Stop>();
        for (var r : alarms.rows(line, null, AlarmGrade.CRITICAL)) {
            var a = alarms.action(r);
            var start = dateTime(r.get("grade_changed_at"));
            if (start == null) start = dateTime(r.get("occurred_at"));
            var end = dateTime(r.get("closed_at"));
            if (end == null || end.isAfter(db.now())) end = db.now();
            if (end.isBefore(start)) continue;
            stops.add(
                    new StopCauseStatisticsService.Stop(
                            number(r, "equipment_id"),
                            number(r, "alarm_id"),
                            StopCauseStatisticsService.Source.ALARM,
                            a == null
                                    ? string(r, "message")
                                    : Objects.toString(a.get("cause"), string(r, "message")),
                            start,
                            end));
        }
        long n = 1;
        for (var i : context.statuses(p.from(), p.to()))
            if (ids.contains(i.equipmentId()) && i.status() == EquipmentStatus.COMMUNICATION_LOST)
                stops.add(
                        new StopCauseStatisticsService.Stop(
                                i.equipmentId(),
                                n++,
                                StopCauseStatisticsService.Source.COMMUNICATION_LOST,
                                "통신 끊김",
                                i.startedAt(),
                                i.endedAt() == null ? db.now() : i.endedAt()));
        return dto(
                com.sfaasproject.stf.operations.service.V7Support.map(
                        "success", true, "data", causes.summarize(stops, p.from(), p.to())),
                StopCauseResponse.class);
    }

    private record LossPiece(
            long equipment,
            long shortageLine,
            OffsetDateTime from,
            OffsetDateTime to,
            BigDecimal hourly,
            BigDecimal price) {}

    private List<LossPiece> lossPieces(Period p, Long line) {
        var ids =
                context.equipment().stream()
                        .filter(e -> line == null || e.lineId() == line)
                        .map(V7Context.Equipment::id)
                        .toList();
        var rows =
                db.rows(
                        "SELECT * FROM assembly_stop WHERE started_at<? AND (ended_at IS NULL OR"
                                + " ended_at>?) ORDER BY started_at,stop_id",
                        time(p.to()),
                        time(p.from()));
        var accepted = new ArrayList<LossPiece>();
        var coveredUntil = p.from();
        for (var r : rows) {
            long eid = number(r, "cause_equipment_id");
            var start = dateTime(r.get("started_at"));
            if (start.isBefore(p.from())) start = p.from();
            if (start.isBefore(coveredUntil)) start = coveredUntil;
            var end = dateTime(r.get("ended_at"));
            if (end == null || end.isAfter(db.now())) end = db.now();
            if (end.isAfter(p.to())) end = p.to();
            if (!end.isAfter(start)) continue;
            accepted.add(
                    new LossPiece(
                            eid,
                            number(r, "shortage_line_id"),
                            start,
                            end,
                            dec(r.get("hourly_output")),
                            dec(r.get("unit_price"))));
            coveredUntil = end;
        }
        return accepted.stream()
                .filter(
                        piece ->
                                line == null
                                        || ids.contains(piece.equipment())
                                        || piece.shortageLine() == line)
                .toList();
    }

    private BigDecimal amount(List<LossPiece> pieces) {
        return actualLoss.calculate(
                pieces.stream()
                        .map(
                                piece ->
                                        new ActualLossCalculator.Interval(
                                                piece.from(),
                                                piece.to(),
                                                piece.hourly(),
                                                piece.price()))
                        .toList());
    }

    private BigDecimal amount(Period p, Long line) {
        return amount(lossPieces(p, line));
    }

    public LossStatisticsResponse losses(OffsetDateTime from, OffsetDateTime to, Long line) {
        id(line);
        var p = period(from, to, db.now());
        var today =
                db.now().atZoneSameInstant(KST).toLocalDate().atStartOfDay(KST).toOffsetDateTime();
        var data = new ArrayList<Map<String, Object>>();
        var catalog =
                context.equipment().stream()
                        .collect(Collectors.toMap(V7Context.Equipment::id, e -> e));
        for (var date = p.from().atZoneSameInstant(KST).toLocalDate();
                !date.isAfter(p.to().minusNanos(1).atZoneSameInstant(KST).toLocalDate());
                date = date.plusDays(1)) {
            var start = date.atStartOfDay(KST).toOffsetDateTime();
            var end = start.plusDays(1);
            if (start.isBefore(p.from())) start = p.from();
            if (end.isAfter(p.to())) end = p.to();
            var pieces = lossPieces(new Period(start, end), line);
            var equipment = pieces.isEmpty() ? null : catalog.get(pieces.getFirst().equipment());
            data.add(
                    com.sfaasproject.stf.operations.service.V7Support.map(
                            "date",
                            date,
                            "lossAmount",
                            amount(pieces),
                            "assemblyStopMinutes",
                            pieces.stream()
                                    .map(x -> minutes(x.from(), x.to()))
                                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                            "causeEquipmentId",
                            equipment == null ? null : equipment.id(),
                            "causeEquipmentName",
                            equipment == null ? null : equipment.name()));
        }
        return dto(
                com.sfaasproject.stf.operations.service.V7Support.map(
                        "success",
                        true,
                        "data",
                        com.sfaasproject.stf.operations.service.V7Support.map(
                                "todayLossAmount",
                                amount(new Period(today, today.plusDays(1)), line),
                                "weeklyLossAmount",
                                amount(new Period(today.minusDays(6), today.plusDays(1)), line),
                                "daily",
                                data)),
                LossStatisticsResponse.class);
    }

    public EquipmentRankingResponse rankings(
            OffsetDateTime from, OffsetDateTime to, Long line, String sort) {
        if (!List.of("faultCount", "downtimeMinutes", "averageRepairMinutes", "lossAmount")
                .contains(sort))
            throw com.sfaasproject.stf.operations.service.Checks.invalid("순위 기준을 확인해 주세요.");
        var p = period(from, to, db.now());
        var rows = selected(p.from(), p.to(), line);
        var intervals = context.statuses(p.from(), p.to());
        var values = new ArrayList<Map<String, Object>>();
        for (var e : equipment(line)) {
            var faults =
                    rows.stream()
                            .filter(
                                    r ->
                                            number(r, "equipment_id") == e.id()
                                                    && "CRITICAL".equals(r.get("grade")))
                            .toList();
            var closed =
                    faults.stream().filter(r -> "CLOSED".equals(r.get("process_state"))).toList();
            var average =
                    closed.isEmpty()
                            ? null
                            : closed.stream()
                                    .map(
                                            r ->
                                                    minutes(
                                                            dateTime(r.get("occurred_at")),
                                                            dateTime(r.get("closed_at"))))
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .divide(
                                            BigDecimal.valueOf(closed.size()),
                                            12,
                                            RoundingMode.HALF_UP);
            var d = durations(e.id(), p, intervals);
            var loss =
                    amount(
                            lossPieces(p, line).stream()
                                    .filter(x -> x.equipment() == e.id())
                                    .toList());
            values.add(
                    com.sfaasproject.stf.operations.service.V7Support.map(
                            "rank",
                            1,
                            "equipmentId",
                            e.id(),
                            "equipmentName",
                            e.name(),
                            "faultCount",
                            faults.size(),
                            "downtimeMinutes",
                            d.get(EquipmentStatus.FAULT)
                                    .add(d.get(EquipmentStatus.COMMUNICATION_LOST)),
                            "averageRepairMinutes",
                            average,
                            "lossAmount",
                            loss));
        }
        values.sort(
                Comparator.comparing(
                                (Map<String, Object> r) -> dec(r.get(sort)),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(r -> ((Number) r.get("equipmentId")).longValue()));
        for (int i = 0; i < values.size(); i++) values.get(i).put("rank", i + 1);
        return dto(
                com.sfaasproject.stf.operations.service.V7Support.map(
                        "success", true, "data", values),
                EquipmentRankingResponse.class);
    }
}
