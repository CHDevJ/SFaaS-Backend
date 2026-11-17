package com.sfaasproject.stf.operations.adapter;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.alarm.service.AlarmComparisonDecisionService;
import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BPorts.*;
import com.sfaasproject.stf.operations.port.BReadPort;
import com.sfaasproject.stf.operations.service.Checks;
import com.sfaasproject.stf.part.service.PartLifeDecisionService;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Real read projections over the V1/V2 schema; SQL inputs are bound parameters. */
public class JdbcBReadPort implements BReadPort {
    private final JdbcBDatabase db;
    private final Users users;
    private final Settings settings;
    private final InventoryRuntime inventory;
    private final Alarms alarms;

    public JdbcBReadPort(
            JdbcBDatabase db,
            Users users,
            Settings settings,
            InventoryRuntime inventory,
            Alarms alarms) {
        this.db = db;
        this.users = users;
        this.settings = settings;
        this.inventory = inventory;
        this.alarms = alarms;
    }

    private <T> T ok(Class<T> type, Object data) {
        return db.convert(map("success", true, "data", data), type);
    }

    private <T> T page(Class<T> type, Object data, int page, int size, int total) {
        return db.convert(
                map(
                        "success",
                        true,
                        "data",
                        data,
                        "meta",
                        map(
                                "page",
                                page,
                                "size",
                                size,
                                "totalElements",
                                total,
                                "totalPages",
                                (total + size - 1) / size)),
                type);
    }

    private List<Map<String, Object>> slice(List<Map<String, Object>> rows, int page, int size) {
        return rows.stream().skip((long) page * size).limit(size).toList();
    }

    private static boolean matches(Long filter, long value) {
        return filter == null || filter == value;
    }

    private static boolean in(OffsetDateTime value, OffsetDateTime from, OffsetDateTime to) {
        return value != null
                && (from == null || !value.isBefore(from))
                && (to == null || !value.isAfter(to));
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private static long seconds(OffsetDateTime from, OffsetDateTime to) {
        return from == null || to == null
                ? 0
                : Math.max(0, Duration.between(from, to).getSeconds());
    }

    private static BigDecimal minutes(OffsetDateTime from, OffsetDateTime to) {
        return BigDecimal.valueOf(seconds(from, to))
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> alarmRows() {
        return db.rows(
                "SELECT a.*,e.name AS equipment_name,e.equip_code,p.line_id,l.name AS"
                    + " line_name,d.name AS"
                    + " item_name,d.unit,d.caution_limit,d.critical_limit,ml.measured_value FROM"
                    + " alarm a JOIN equipment e ON e.equipment_id=a.equipment_id JOIN process p ON"
                    + " p.process_id=e.process_id JOIN line l ON l.line_id=p.line_id JOIN data_item"
                    + " d ON d.data_item_id=a.data_item_id LEFT JOIN measurement_latest ml ON"
                    + " ml.data_item_id=d.data_item_id WHERE e.deleted_at IS NULL AND d.deleted_at"
                    + " IS NULL AND l.deleted_at IS NULL ORDER BY a.occurred_at DESC,a.alarm_id"
                    + " DESC");
    }

    private Map<String, Object> alarmRow(long id) {
        return alarmRows().stream()
                .filter(r -> number(r, "alarm_id") == id)
                .findFirst()
                .orElseThrow(() -> Checks.missing("ALARM_NOT_FOUND"));
    }

    private Map<String, Object> alarmDto(Map<String, Object> r) {
        String process = string(r, "process_state");
        if ("ACTION_TAKEN".equals(process)) process = "ACTION";
        var at = dateTime(r.get("occurred_at"));
        return map(
                "alarmId",
                r.get("alarm_id"),
                "equipmentId",
                r.get("equipment_id"),
                "equipmentCode",
                r.get("equip_code"),
                "equipmentName",
                r.get("equipment_name"),
                "lineId",
                r.get("line_id"),
                "lineName",
                r.get("line_name"),
                "dataItemId",
                r.get("data_item_id"),
                "dataItemName",
                r.get("item_name"),
                "alarmMessage",
                r.get("message"),
                "currentValue",
                r.get("measured_value") == null ? r.get("trigger_value") : r.get("measured_value"),
                "unit",
                r.get("unit"),
                "thresholdValue",
                r.get("limit_value"),
                "grade",
                r.get("grade"),
                "valueStatus",
                r.get("value_state"),
                "processStatus",
                process,
                "assignee",
                r.get("assignee_id") == null ? null : users.historical(number(r, "assignee_id")),
                "occurredAt",
                at,
                "elapsedSeconds",
                seconds(at, r.get("closed_at") == null ? db.now() : dateTime(r.get("closed_at"))),
                "acknowledgementDelayed",
                r.get("confirmed_at") == null
                        && at.plusMinutes(settings.integer("RENOTIFY_MIN", 3)).isBefore(db.now()));
    }

    private void applySort(List<Map<String, Object>> rows, String sort, Set<String> allowed) {
        if (sort == null) return;
        String[] parts = sort.split(",");
        if (!allowed.contains(parts[0])) throw Checks.invalid("지원하지 않는 정렬 항목입니다.");
        Comparator<Map<String, Object>> comparator =
                (left, right) -> {
                    Object a = left.get(parts[0]), b = right.get(parts[0]);
                    if (a == null) return b == null ? 0 : 1;
                    if (b == null) return -1;
                    if (a instanceof Number && b instanceof Number)
                        return decimal(a).compareTo(decimal(b));
                    return a.toString().compareTo(b.toString());
                };
        if (parts.length == 2 && parts[1].equals("desc")) comparator = comparator.reversed();
        rows.sort(comparator);
    }

    @Override
    public AlarmPageResponse getActiveAlarms(GetActiveAlarmsQuery q) {
        var filtered =
                alarmRows().stream()
                        .filter(r -> !"CLOSED".equals(string(r, "process_state")))
                        .filter(
                                r ->
                                        matches(q.lineId(), number(r, "line_id"))
                                                && matches(
                                                        q.equipmentId(), number(r, "equipment_id")))
                        .filter(
                                r ->
                                        q.grade() == null
                                                || q.grade().name().equals(string(r, "grade")))
                        .map(this::alarmDto)
                        .filter(
                                r ->
                                        q.processStatus() == null
                                                || q.processStatus()
                                                        .name()
                                                        .equals(r.get("processStatus")))
                        .collect(Collectors.toCollection(ArrayList::new));
        filtered.sort(
                Comparator.<Map<String, Object>, Boolean>comparing(
                                r -> !"UNACKNOWLEDGED".equals(r.get("processStatus")))
                        .thenComparing(r -> !"CRITICAL".equals(r.get("grade")))
                        .thenComparing(
                                r -> (OffsetDateTime) r.get("occurredAt"),
                                Comparator.reverseOrder()));
        applySort(
                filtered,
                q.sort(),
                Set.of("occurredAt", "grade", "processStatus", "equipmentName", "elapsedSeconds"));
        int page = q.page() == null ? 0 : q.page(), size = q.size() == null ? 20 : q.size();
        return page(
                AlarmPageResponse.class,
                map(
                        "activeCount",
                        filtered.size(),
                        "unacknowledgedCount",
                        filtered.stream()
                                .filter(r -> "UNACKNOWLEDGED".equals(r.get("processStatus")))
                                .count(),
                        "items",
                        slice(filtered, page, size)),
                page,
                size,
                filtered.size());
    }

    @Override
    public RecentClosedAlarmResponse getRecentClosedAlarms(GetRecentClosedAlarmsQuery q) {
        var list =
                alarmRows().stream()
                        .filter(r -> r.get("closed_at") != null)
                        .sorted(
                                Comparator.comparing(
                                        r -> dateTime(r.get("closed_at")),
                                        Comparator.reverseOrder()))
                        .limit(q.limit() == null ? 20 : q.limit())
                        .map(
                                r ->
                                        map(
                                                "alarmId",
                                                r.get("alarm_id"),
                                                "closedAt",
                                                dateTime(r.get("closed_at")),
                                                "equipmentName",
                                                r.get("equipment_name"),
                                                "alarmMessage",
                                                r.get("message"),
                                                "durationSeconds",
                                                seconds(
                                                        dateTime(r.get("occurred_at")),
                                                        dateTime(r.get("closed_at"))),
                                                "result",
                                                r.get("process_result"),
                                                "acknowledger",
                                                r.get("confirmed_by") == null
                                                        ? null
                                                        : users.historical(
                                                                number(r, "confirmed_by"))))
                        .toList();
        return ok(RecentClosedAlarmResponse.class, list);
    }

    @Override
    public AlarmDetailResponse getAlarmDetail(GetAlarmDetailQuery q) {
        var r = alarmRow(q.alarmId());
        var data = alarmDto(r);
        var at = dateTime(r.get("occurred_at"));
        data.putAll(
                map(
                        "acknowledgedAt",
                        dateTime(r.get("confirmed_at")),
                        "actionedAt",
                        dateTime(r.get("acted_at")),
                        "recoveredAt",
                        dateTime(r.get("recovered_at")),
                        "closedAt",
                        dateTime(r.get("closed_at")),
                        "recurrenceCount",
                        number(r, "recurrence_count"),
                        "result",
                        r.get("process_result"),
                        "trendBeforeAndAfter",
                        points(number(r, "data_item_id"), at.minusMinutes(10), at.plusMinutes(10)),
                        "timeline",
                        timeline(q.alarmId())));
        return ok(AlarmDetailResponse.class, data);
    }

    private List<Map<String, Object>> points(long item, OffsetDateTime from, OffsetDateTime to) {
        return db
                .rows(
                        "SELECT measured_at,measured_value FROM measurement WHERE data_item_id=?"
                                + " AND measured_at>=? AND measured_at<=? ORDER BY measured_at",
                        item,
                        time(from),
                        time(to))
                .stream()
                .map(
                        r ->
                                map(
                                        "measuredAt",
                                        dateTime(r.get("measured_at")),
                                        "value",
                                        r.get("measured_value")))
                .toList();
    }

    private List<Map<String, Object>> timeline(long id) {
        return db
                .rows("SELECT * FROM alarm_log WHERE alarm_id=? ORDER BY created_at,log_id", id)
                .stream()
                .map(
                        r ->
                                map(
                                        "actionType",
                                        r.get("event_type"),
                                        "recordedAt",
                                        dateTime(r.get("created_at")),
                                        "actor",
                                        r.get("actor_id") == null
                                                ? null
                                                : users.historical(number(r, "actor_id")),
                                        "content",
                                        r.get("content") == null ? "" : r.get("content")))
                .toList();
    }

    @Override
    public AlarmComparisonResponse getAlarmComparison(GetAlarmComparisonQuery q) {
        var r = alarmRow(q.alarmId());
        var at = dateTime(r.get("occurred_at"));
        var ps = points(number(r, "data_item_id"), at.minusMinutes(10), at.plusMinutes(10));
        var result =
                new AlarmComparisonDecisionService()
                        .compare(
                                at.toLocalDateTime(),
                                ps.stream()
                                        .map(
                                                p ->
                                                        new AlarmComparisonDecisionService.Point(
                                                                ((OffsetDateTime)
                                                                                p.get("measuredAt"))
                                                                        .toLocalDateTime(),
                                                                decimal(p.get("value"))))
                                        .toList());
        return ok(
                AlarmComparisonResponse.class,
                map(
                        "alarmId",
                        q.alarmId(),
                        "equipmentId",
                        r.get("equipment_id"),
                        "dataItemId",
                        r.get("data_item_id"),
                        "dataItemName",
                        r.get("item_name"),
                        "unit",
                        r.get("unit"),
                        "occurredAt",
                        at,
                        "cautionThreshold",
                        r.get("caution_limit"),
                        "criticalThreshold",
                        r.get("critical_limit"),
                        "beforeAverage",
                        result.beforeAverage(),
                        "afterAverage",
                        result.afterAverage(),
                        "changeRatePercent",
                        result.changeRatePercent(),
                        "before",
                        ps.stream()
                                .filter(p -> ((OffsetDateTime) p.get("measuredAt")).isBefore(at))
                                .toList(),
                        "after",
                        ps.stream()
                                .filter(p -> !((OffsetDateTime) p.get("measuredAt")).isBefore(at))
                                .toList()));
    }

    @Override
    public RecommendedActionResponse getRecommendedActions(GetRecommendedActionsQuery q) {
        var source = alarmRow(q.alarmId());
        var rows =
                db.rows(
                        "SELECT aa.action_content,aa.effect_result,aa.acted_at,a.closed_at FROM"
                            + " alarm_action aa JOIN alarm a ON a.alarm_id=aa.alarm_id WHERE"
                            + " a.equipment_id=? AND a.data_item_id=? AND a.closed_at IS NOT NULL",
                        source.get("equipment_id"),
                        source.get("data_item_id"));
        var groups = rows.stream().collect(Collectors.groupingBy(r -> string(r, "action_content")));
        var result =
                groups.entrySet().stream()
                        .map(
                                e -> {
                                    int count =
                                            (int)
                                                    e.getValue().stream()
                                                            .filter(
                                                                    r ->
                                                                            "EFFECTIVE"
                                                                                    .equals(
                                                                                            string(
                                                                                                    r,
                                                                                                    "effect_result")))
                                                            .count();
                                    return map(
                                            "actionContent",
                                            e.getKey(),
                                            "effectiveCount",
                                            count,
                                            "totalCount",
                                            e.getValue().size(),
                                            "effectivenessRate",
                                            100.0 * count / e.getValue().size(),
                                            "averageRepairMinutes",
                                            e.getValue().stream()
                                                    .mapToDouble(
                                                            r ->
                                                                    minutes(
                                                                                    dateTime(
                                                                                            r.get(
                                                                                                    "acted_at")),
                                                                                    dateTime(
                                                                                            r.get(
                                                                                                    "closed_at")))
                                                                            .doubleValue())
                                                    .average()
                                                    .orElse(0));
                                })
                        .sorted(
                                Comparator.comparingDouble(
                                        r -> -((Number) r.get("effectivenessRate")).doubleValue()))
                        .toList();
        return ok(RecommendedActionResponse.class, result);
    }

    @Override
    public AlarmEffectResponse getAlarmEffect(GetAlarmEffectQuery q) {
        var a = alarms.lock(q.alarmId());
        if (a == null) throw Checks.missing("ALARM_NOT_FOUND");
        var s = a.state();
        return ok(
                AlarmEffectResponse.class,
                map(
                        "alarmId",
                        a.id(),
                        "processStatus",
                        s.processStatus().name(),
                        "effectStatus",
                        s.effectStatus().name(),
                        "startedAt",
                        s.verificationStartedAt() == null
                                ? null
                                : s.verificationStartedAt()
                                        .atZone(db.clock.getZone())
                                        .toOffsetDateTime(),
                        "currentProductionCount",
                        s.currentProductionCount(),
                        "targetProductionCount",
                        Math.max(
                                1,
                                s.targetProductionCount() == 0
                                        ? settings.integer("VERIFY_PASS_COUNT", 20)
                                        : s.targetProductionCount()),
                        "recurrenceCount",
                        s.recurrenceCount()));
    }

    @Override
    public AlarmHistoryPageResponse getAlarmHistories(GetAlarmHistoriesQuery q) {
        var rows =
                alarmRows().stream()
                        .filter(
                                r ->
                                        r.get("closed_at") != null
                                                && in(
                                                        dateTime(r.get("occurred_at")),
                                                        q.from(),
                                                        q.to())
                                                && matches(q.lineId(), number(r, "line_id"))
                                                && matches(
                                                        q.equipmentId(), number(r, "equipment_id")))
                        .filter(
                                r ->
                                        q.grade() == null
                                                || q.grade().name().equals(string(r, "grade")))
                        .filter(
                                r ->
                                        q.result() == null
                                                || q.result()
                                                        .name()
                                                        .equals(string(r, "process_result")))
                        .map(
                                r ->
                                        map(
                                                "alarmId",
                                                r.get("alarm_id"),
                                                "occurredAt",
                                                dateTime(r.get("occurred_at")),
                                                "closedAt",
                                                dateTime(r.get("closed_at")),
                                                "equipmentName",
                                                r.get("equipment_name"),
                                                "alarmMessage",
                                                r.get("message"),
                                                "grade",
                                                r.get("grade"),
                                                "result",
                                                r.get("process_result"),
                                                "acknowledgementSeconds",
                                                seconds(
                                                        dateTime(r.get("occurred_at")),
                                                        dateTime(r.get("confirmed_at"))),
                                                "repairSeconds",
                                                seconds(
                                                        dateTime(r.get("occurred_at")),
                                                        dateTime(r.get("closed_at"))),
                                                "assignee",
                                                r.get("assignee_id") == null
                                                        ? null
                                                        : users.historical(
                                                                number(r, "assignee_id"))))
                        .toList();
        var sortedRows = new ArrayList<>(rows);
        applySort(sortedRows, q.sort(), Set.of("occurredAt", "closedAt", "grade", "equipmentName"));
        int p = q.page() == null ? 0 : q.page(), size = q.size() == null ? 20 : q.size();
        return page(
                AlarmHistoryPageResponse.class, slice(sortedRows, p, size), p, size, rows.size());
    }

    @Override
    public FaultHistoryResponse getEquipmentFaultHistories(GetEquipmentFaultHistoriesQuery q) {
        requireEquipment(q.equipmentId());
        var rows =
                alarmRows().stream()
                        .filter(
                                r ->
                                        number(r, "equipment_id") == q.equipmentId()
                                                && "CRITICAL".equals(string(r, "grade"))
                                                && r.get("closed_at") != null
                                                && in(
                                                        dateTime(r.get("occurred_at")),
                                                        q.from(),
                                                        q.to()))
                        .toList();
        var items =
                rows.stream()
                        .map(
                                r -> {
                                    var action =
                                            db.one(
                                                    "SELECT cause,action_content FROM alarm_action"
                                                            + " WHERE alarm_id=? ORDER BY acted_at"
                                                            + " DESC,action_id DESC LIMIT 1",
                                                    r.get("alarm_id"));
                                    return map(
                                            "alarmId",
                                            r.get("alarm_id"),
                                            "occurredAt",
                                            dateTime(r.get("occurred_at")),
                                            "closedAt",
                                            dateTime(r.get("closed_at")),
                                            "cause",
                                            action == null ? "" : action.get("cause"),
                                            "actionContent",
                                            action == null ? "" : action.get("action_content"),
                                            "repairMinutes",
                                            minutes(
                                                    dateTime(r.get("occurred_at")),
                                                    dateTime(r.get("closed_at"))),
                                            "result",
                                            r.get("process_result"));
                                })
                        .toList();
        double avg =
                items.stream()
                        .mapToDouble(r -> decimal(r.get("repairMinutes")).doubleValue())
                        .average()
                        .orElse(0);
        var times = rows.stream().map(r -> dateTime(r.get("occurred_at"))).sorted().toList();
        long gaps = 0;
        for (int i = 1; i < times.size(); i++) gaps += seconds(times.get(i - 1), times.get(i));
        return ok(
                FaultHistoryResponse.class,
                map(
                        "monthlyFaultCount",
                        rows.stream()
                                .filter(
                                        r ->
                                                YearMonth.from(dateTime(r.get("occurred_at")))
                                                        .equals(YearMonth.from(db.now())))
                                .count(),
                        "averageRepairMinutes",
                        avg,
                        "averageFailureIntervalMinutes",
                        times.size() < 2 ? 0 : gaps / 60.0 / (times.size() - 1),
                        "items",
                        items));
    }

    @Override
    public InventoryResponse getInventory(GetInventoryQuery q) {
        return new InventoryResponse(true, inventory.recalculate(db.now()));
    }

    @Override
    public InventoryHistoryResponse getInventoryHistory(GetInventoryHistoryQuery q) {
        var list =
                db
                        .rows(
                                "SELECT s.*,l.name FROM stock_history s JOIN line l ON"
                                        + " l.line_id=s.line_id WHERE l.deleted_at IS NULL AND"
                                        + " recorded_at>=? AND recorded_at<=? ORDER BY"
                                        + " recorded_at,line_id",
                                time(q.from()),
                                time(q.to()))
                        .stream()
                        .filter(
                                r ->
                                        q.lineIds() == null
                                                || q.lineIds().contains(number(r, "line_id")))
                        .map(
                                r ->
                                        map(
                                                "recordedAt",
                                                dateTime(r.get("recorded_at")),
                                                "lineId",
                                                r.get("line_id"),
                                                "lineName",
                                                r.get("name"),
                                                "quantity",
                                                r.get("qty")))
                        .toList();
        return ok(InventoryHistoryResponse.class, list);
    }

    private Map<String, Object> forecast(InventoryLine line) {
        BigDecimal net = line.consumptionPerMinute().subtract(line.productionPerMinute());
        Integer remaining =
                net.signum() <= 0
                        ? null
                        : BigDecimal.valueOf(line.quantity())
                                .divide(net, 0, RoundingMode.CEILING)
                                .intValueExact();
        String stage =
                remaining == null
                        ? "MAINTAIN"
                        : remaining <= settings.integer("STOCK_DANGER_MIN", 10)
                                ? "DANGER"
                                : remaining <= settings.integer("STOCK_LEVEL_MIN", 30)
                                        ? "CAUTION"
                                        : "MAINTAIN";
        return map(
                "lineId",
                line.lineId(),
                "lineName",
                line.lineName(),
                "quantity",
                line.quantity(),
                "consumptionPerMinute",
                line.consumptionPerMinute(),
                "productionPerMinute",
                line.productionPerMinute(),
                "calculatedAt",
                line.calculatedAt(),
                "stage",
                stage,
                "netConsumptionPerMinute",
                net,
                "remainingMinutes",
                remaining,
                "expectedDepletionAt",
                remaining == null ? null : db.now().plusMinutes(remaining));
    }

    @Override
    public InventoryDetailResponse getInventoryDetail(GetInventoryDetailQuery q) {
        var line =
                inventory.recalculate(db.now()).stream()
                        .filter(l -> l.lineId().equals(q.lineId()))
                        .findFirst()
                        .orElseThrow(() -> Checks.missing("LINE_NOT_FOUND"));
        return ok(InventoryDetailResponse.class, forecast(line));
    }

    @Override
    public AssemblyStopForecastResponse getAssemblyStopForecast(GetAssemblyStopForecastQuery q) {
        var lines = new JdbcBForecasts(db, inventory, settings).lines();
        var first =
                lines.stream()
                        .filter(l -> l.get("assemblyStopExpectedAt") != null)
                        .min(
                                Comparator.comparing(
                                        l -> (OffsetDateTime) l.get("assemblyStopExpectedAt")))
                        .orElse(null);
        return ok(
                AssemblyStopForecastResponse.class,
                map(
                        "lines",
                        lines,
                        "representativeLineId",
                        first == null ? null : first.get("lineId"),
                        "earliestAssemblyStopExpectedAt",
                        first == null ? null : first.get("assemblyStopExpectedAt")));
    }

    @Override
    public EquipmentLayoutResponse getEquipmentLayout(GetEquipmentLayoutQuery q) {
        var rows =
                db.rows(
                        "SELECT e.*,p.line_id,p.process_id,p.process_seq,p.name AS"
                            + " process_name,l.name AS line_name,es.status,es.status_since FROM"
                            + " equipment e JOIN process p ON p.process_id=e.process_id JOIN line l"
                            + " ON l.line_id=p.line_id LEFT JOIN equipment_state es ON"
                            + " es.equipment_id=e.equipment_id WHERE e.deleted_at IS NULL AND"
                            + " l.deleted_at IS NULL ORDER BY"
                            + " l.sort_order,p.process_seq,e.equipment_id");
        var items =
                rows.stream()
                        .filter(r -> matches(q.lineId(), number(r, "line_id")))
                        .map(
                                r -> {
                                    long id = number(r, "equipment_id");
                                    var measurements =
                                            db
                                                    .rows(
                                                            "SELECT"
                                                                + " d.*,ml.measured_value,ml.measured_at,ml.value_grade"
                                                                + " FROM data_item d JOIN"
                                                                + " measurement_latest ml ON"
                                                                + " ml.data_item_id=d.data_item_id"
                                                                + " WHERE d.equipment_id=? AND"
                                                                + " d.deleted_at IS NULL ORDER BY"
                                                                + " d.sort_order,d.data_item_id",
                                                            id)
                                                    .stream()
                                                    .map(
                                                            d ->
                                                                    map(
                                                                            "dataItemId",
                                                                            d.get("data_item_id"),
                                                                            "dataItemName",
                                                                            d.get("name"),
                                                                            "valueType",
                                                                            d.get("item_kind"),
                                                                            "value",
                                                                            d.get("measured_value"),
                                                                            "unit",
                                                                            d.get("unit"),
                                                                            "valueStatus",
                                                                            d.get("value_grade"),
                                                                            "measuredAt",
                                                                            dateTime(
                                                                                    d.get(
                                                                                            "measured_at")),
                                                                            "cautionThreshold",
                                                                            d.get("caution_limit"),
                                                                            "criticalThreshold",
                                                                            d.get(
                                                                                    "critical_limit")))
                                                    .toList();
                                    int count =
                                            db.jdbc.queryForObject(
                                                    "SELECT COUNT(*) FROM alarm WHERE"
                                                            + " equipment_id=? AND"
                                                            + " process_state<>'CLOSED'",
                                                    Integer.class,
                                                    id);
                                    return map(
                                            "equipmentId",
                                            id,
                                            "equipmentCode",
                                            r.get("equip_code"),
                                            "equipmentName",
                                            r.get("name"),
                                            "lineId",
                                            r.get("line_id"),
                                            "lineName",
                                            r.get("line_name"),
                                            "processId",
                                            r.get("process_id"),
                                            "processName",
                                            r.get("process_name"),
                                            "processOrder",
                                            r.get("process_seq"),
                                            "status",
                                            r.get("status") == null
                                                    ? "COMMUNICATION_LOST"
                                                    : r.get("status"),
                                            "statusStartedAt",
                                            r.get("status_since") == null
                                                    ? dateTime(r.get("created_at"))
                                                    : dateTime(r.get("status_since")),
                                            "activeAlarmCount",
                                            count,
                                            "mainMeasurements",
                                            measurements);
                                })
                        .toList();
        var counts = new ArrayList<Map<String, Object>>();
        for (var status : EquipmentStatus.values())
            counts.add(
                    map(
                            "status",
                            status.name(),
                            "count",
                            items.stream()
                                    .filter(i -> status.name().equals(i.get("status")))
                                    .count()));
        return ok(EquipmentLayoutResponse.class, map("equipment", items, "statusCounts", counts));
    }

    private void requireEquipment(long id) {
        if (db.one(
                        "SELECT equipment_id FROM equipment WHERE equipment_id=? AND deleted_at IS"
                                + " NULL",
                        id)
                == null) throw Checks.missing("EQUIPMENT_NOT_FOUND");
    }

    private List<Map<String, Object>> partRows() {
        return db.rows(
                "SELECT pt.*,e.name AS equipment_name,p.line_id,p.process_id,p.name AS"
                    + " process_name,l.name AS line_name FROM part pt JOIN equipment e ON"
                    + " e.equipment_id=pt.equipment_id JOIN process p ON p.process_id=e.process_id"
                    + " JOIN line l ON l.line_id=p.line_id WHERE pt.deleted_at IS NULL AND"
                    + " e.deleted_at IS NULL AND l.deleted_at IS NULL ORDER BY pt.part_id");
    }

    private Map<String, Object> partDto(Map<String, Object> r) {
        var now = db.now();
        var start = dateTime(r.get("last_replaced_at"));
        var runs =
                db
                        .rows(
                                "SELECT started_at,ended_at FROM equipment_status_history WHERE"
                                    + " equipment_id=? AND status='RUNNING' AND (ended_at IS NULL"
                                    + " OR ended_at>=?)",
                                r.get("equipment_id"),
                                time(start))
                        .stream()
                        .map(
                                x ->
                                        new PartLifeDecisionService.RunningInterval(
                                                dateTime(x.get("started_at")).toLocalDateTime(),
                                                x.get("ended_at") == null
                                                        ? null
                                                        : dateTime(x.get("ended_at"))
                                                                .toLocalDateTime()))
                        .toList();
        var usage =
                new PartLifeDecisionService()
                        .calculate(
                                PartLifeDecisionService.Basis.valueOf(string(r, "manage_basis")),
                                start.toLocalDateTime(),
                                decimal(r.get("replace_limit")),
                                runs,
                                now.toLocalDateTime(),
                                settings.decimal("PART_DUE_SOON_PERCENT", BigDecimal.valueOf(80)));
        return map(
                "partId",
                r.get("part_id"),
                "partName",
                r.get("name"),
                "specification",
                r.get("spec"),
                "managementNumber",
                r.get("manage_no"),
                "lifeBasis",
                r.get("manage_basis"),
                "usage",
                usage.used(),
                "replacementCriterion",
                usage.criterion(),
                "usageRate",
                usage.usageRate(),
                "remaining",
                usage.remaining(),
                "expectedCriterionAt",
                usage.expectedCriterionAt() == null
                        ? null
                        : usage.expectedCriterionAt().atZone(db.clock.getZone()).toOffsetDateTime(),
                "status",
                usage.status().name(),
                "equipmentId",
                r.get("equipment_id"),
                "equipmentName",
                r.get("equipment_name"),
                "lineName",
                r.get("line_name"),
                "processName",
                r.get("process_name"));
    }

    @Override
    public InstalledPartListResponse getEquipmentParts(GetEquipmentPartsQuery q) {
        requireEquipment(q.equipmentId());
        return ok(
                InstalledPartListResponse.class,
                partRows().stream()
                        .filter(r -> number(r, "equipment_id") == q.equipmentId())
                        .map(this::partDto)
                        .map(
                                r -> {
                                    r.remove("equipmentId");
                                    r.remove("equipmentName");
                                    r.remove("lineName");
                                    r.remove("processName");
                                    return r;
                                })
                        .toList());
    }

    @Override
    public PartUsagePageResponse getPartUsage(GetPartUsageQuery q) {
        var rows =
                partRows().stream()
                        .filter(
                                r ->
                                        matches(q.lineId(), number(r, "line_id"))
                                                && matches(q.processId(), number(r, "process_id"))
                                                && matches(
                                                        q.equipmentId(), number(r, "equipment_id")))
                        .map(this::partDto)
                        .filter(
                                r ->
                                        q.status() == null
                                                || q.status().name().equals(r.get("status")))
                        .toList();
        int p = q.page() == null ? 0 : q.page(), s = q.size() == null ? 20 : q.size();
        return page(PartUsagePageResponse.class, slice(rows, p, s), p, s, rows.size());
    }

    @Override
    public PartReplacementHistoryResponse getPartReplacementHistories(
            GetPartReplacementHistoriesQuery q) {
        var part =
                partRows().stream()
                        .filter(
                                r ->
                                        number(r, "part_id") == q.partId()
                                                && number(r, "equipment_id") == q.equipmentId())
                        .findFirst()
                        .orElseThrow(() -> Checks.missing("PART_NOT_FOUND"));
        var list =
                db
                        .rows(
                                "SELECT * FROM part_replacement WHERE part_id=? ORDER BY"
                                        + " replaced_at DESC,replacement_id DESC",
                                q.partId())
                        .stream()
                        .filter(r -> in(dateTime(r.get("replaced_at")), q.from(), q.to()))
                        .map(
                                r ->
                                        map(
                                                "replacementId",
                                                r.get("replacement_id"),
                                                "replacedAt",
                                                dateTime(r.get("replaced_at")),
                                                "partId",
                                                q.partId(),
                                                "partName",
                                                part.get("name"),
                                                "reason",
                                                r.get("reason"),
                                                "workContent",
                                                r.get("work_content"),
                                                "worker",
                                                users.historical(number(r, "worker_id"))))
                        .toList();
        return ok(
                PartReplacementHistoryResponse.class,
                map(
                        "items",
                        list,
                        "averageReplacementInterval",
                        averageInterval(list, "replacedAt")));
    }

    private BigDecimal averageInterval(List<Map<String, Object>> values, String key) {
        var times = values.stream().map(r -> (OffsetDateTime) r.get(key)).sorted().toList();
        if (times.size() < 2) return null;
        long secs = 0;
        for (int i = 1; i < times.size(); i++) secs += seconds(times.get(i - 1), times.get(i));
        return BigDecimal.valueOf(secs)
                .divide(BigDecimal.valueOf(86400L * (times.size() - 1)), 4, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> stats(OffsetDateTime from, OffsetDateTime to, Long line) {
        return alarmRows().stream()
                .filter(
                        r ->
                                in(dateTime(r.get("occurred_at")), from, to)
                                        && matches(line, number(r, "line_id")))
                .toList();
    }

    @Override
    public AlarmResponseStatisticsResponse getAlarmResponseStatistics(
            GetAlarmResponseStatisticsQuery q) {
        var rows = stats(q.from(), q.to(), q.lineId());
        double
                ack =
                        rows.stream()
                                .filter(r -> r.get("confirmed_at") != null)
                                .mapToDouble(
                                        r ->
                                                minutes(
                                                                dateTime(r.get("occurred_at")),
                                                                dateTime(r.get("confirmed_at")))
                                                        .doubleValue())
                                .average()
                                .orElse(0),
                repair =
                        rows.stream()
                                .filter(
                                        r ->
                                                r.get("closed_at") != null
                                                        && !"FALSE_POSITIVE"
                                                                .equals(
                                                                        string(
                                                                                r,
                                                                                "process_result")))
                                .mapToDouble(
                                        r ->
                                                minutes(
                                                                dateTime(r.get("occurred_at")),
                                                                dateTime(r.get("closed_at")))
                                                        .doubleValue())
                                .average()
                                .orElse(0);
        long delayed =
                rows.stream()
                        .filter(
                                r ->
                                        seconds(
                                                        dateTime(r.get("occurred_at")),
                                                        r.get("confirmed_at") == null
                                                                ? db.now()
                                                                : dateTime(r.get("confirmed_at")))
                                                > settings.integer("RENOTIFY_MIN", 3) * 60L)
                        .count();
        return ok(
                AlarmResponseStatisticsResponse.class,
                map(
                        "averageAcknowledgementMinutes",
                        ack,
                        "averageRepairMinutes",
                        repair,
                        "delayedAlarmCount",
                        delayed,
                        "delayedAlarmRate",
                        rows.isEmpty() ? 0 : 100.0 * delayed / rows.size()));
    }

    @Override
    public FalsePositiveStatisticsResponse getFalsePositiveStatistics(
            GetFalsePositiveStatisticsQuery q) {
        var rows = stats(q.from(), q.to(), q.lineId());
        var falseRows =
                rows.stream()
                        .filter(r -> "FALSE_POSITIVE".equals(string(r, "process_result")))
                        .toList();
        var byEquipment =
                falseRows.stream().collect(Collectors.groupingBy(r -> number(r, "equipment_id")));
        var eq =
                byEquipment.entrySet().stream()
                        .map(
                                e ->
                                        map(
                                                "id",
                                                e.getKey(),
                                                "name",
                                                e.getValue().get(0).get("equipment_name"),
                                                "count",
                                                e.getValue().size()))
                        .sorted(
                                Comparator.comparingLong(
                                        r -> -((Number) r.get("count")).longValue()))
                        .toList();
        var byItem = rows.stream().collect(Collectors.groupingBy(r -> number(r, "data_item_id")));
        var rates =
                byItem.entrySet().stream()
                        .map(
                                e ->
                                        map(
                                                "id",
                                                e.getKey(),
                                                "name",
                                                e.getValue().get(0).get("item_name"),
                                                "rate",
                                                100.0
                                                        * e.getValue().stream()
                                                                .filter(
                                                                        r ->
                                                                                "FALSE_POSITIVE"
                                                                                        .equals(
                                                                                                string(
                                                                                                        r,
                                                                                                        "process_result")))
                                                                .count()
                                                        / e.getValue().size()))
                        .toList();
        double response =
                falseRows.stream()
                        .filter(r -> r.get("confirmed_at") != null)
                        .mapToDouble(
                                r ->
                                        minutes(
                                                        dateTime(r.get("occurred_at")),
                                                        dateTime(r.get("confirmed_at")))
                                                .doubleValue())
                        .average()
                        .orElse(0);
        return ok(
                FalsePositiveStatisticsResponse.class,
                map(
                        "count",
                        falseRows.size(),
                        "rate",
                        rows.isEmpty() ? 0 : 100.0 * falseRows.size() / rows.size(),
                        "responseMinutes",
                        response,
                        "equipmentRanking",
                        eq,
                        "dataItemRates",
                        rates));
    }

    @Override
    public DailyAlarmStatisticsResponse getDailyAlarmStatistics(GetDailyAlarmStatisticsQuery q) {
        var rows = stats(q.from(), q.to(), q.lineId());
        var list = new ArrayList<Map<String, Object>>();
        for (LocalDate date = q.from().toLocalDate();
                !date.isAfter(q.to().toLocalDate());
                date = date.plusDays(1)) {
            final LocalDate day = date;
            var matching =
                    rows.stream()
                            .filter(r -> dateTime(r.get("occurred_at")).toLocalDate().equals(day))
                            .toList();
            list.add(
                    map(
                            "date",
                            date,
                            "criticalCount",
                            matching.stream()
                                    .filter(r -> "CRITICAL".equals(string(r, "grade")))
                                    .count(),
                            "cautionCount",
                            matching.stream()
                                    .filter(r -> "CAUTION".equals(string(r, "grade")))
                                    .count(),
                            "falsePositiveCount",
                            matching.stream()
                                    .filter(
                                            r ->
                                                    "FALSE_POSITIVE"
                                                            .equals(string(r, "process_result")))
                                    .count()));
        }
        return ok(DailyAlarmStatisticsResponse.class, list);
    }

    private List<Map<String, Object>> durations(OffsetDateTime from, OffsetDateTime to, Long line) {
        var equipment =
                db.rows(
                        "SELECT e.equipment_id,e.name,p.line_id FROM equipment e JOIN process p ON"
                                + " p.process_id=e.process_id WHERE e.deleted_at IS NULL");
        var list = new ArrayList<Map<String, Object>>();
        for (var e : equipment) {
            if (!matches(line, number(e, "line_id"))) continue;
            var mins = new HashMap<String, BigDecimal>();
            for (var h :
                    db.rows(
                            "SELECT status,started_at,ended_at FROM equipment_status_history WHERE"
                                    + " equipment_id=? AND started_at<? AND (ended_at IS NULL OR"
                                    + " ended_at>?)",
                            e.get("equipment_id"),
                            time(to),
                            time(from))) {
                var start = dateTime(h.get("started_at"));
                var end = h.get("ended_at") == null ? to : dateTime(h.get("ended_at"));
                if (start.isBefore(from)) start = from;
                if (end.isAfter(to)) end = to;
                mins.merge(string(h, "status"), minutes(start, end), BigDecimal::add);
            }
            list.add(
                    map(
                            "equipmentId",
                            e.get("equipment_id"),
                            "equipmentName",
                            e.get("name"),
                            "lineId",
                            e.get("line_id"),
                            "faultMinutes",
                            mins.getOrDefault("FAULT", BigDecimal.ZERO),
                            "idleMinutes",
                            mins.getOrDefault("IDLE", BigDecimal.ZERO),
                            "communicationLostMinutes",
                            mins.getOrDefault("COMMUNICATION_LOST", BigDecimal.ZERO),
                            "runningMinutes",
                            mins.getOrDefault("RUNNING", BigDecimal.ZERO)));
        }
        return list;
    }

    @Override
    public DowntimeStatisticsResponse getDowntimeStatistics(GetDowntimeStatisticsQuery q) {
        return ok(
                DowntimeStatisticsResponse.class,
                durations(q.from(), q.to(), q.lineId()).stream()
                        .map(
                                r ->
                                        map(
                                                "equipmentId",
                                                r.get("equipmentId"),
                                                "equipmentName",
                                                r.get("equipmentName"),
                                                "faultMinutes",
                                                r.get("faultMinutes"),
                                                "idleMinutes",
                                                r.get("idleMinutes"),
                                                "communicationLostMinutes",
                                                r.get("communicationLostMinutes")))
                        .toList());
    }

    @Override
    public AvailabilityStatisticsResponse getAvailabilityStatistics(
            GetAvailabilityStatisticsQuery q) {
        BigDecimal planned = minutes(q.from(), q.to());
        if (planned.signum() <= 0) throw Checks.invalid("조회 종료는 시작 이후여야 합니다.");
        var source = durations(q.from(), q.to(), q.lineId());
        var result = new ArrayList<Map<String, Object>>();
        for (var row : source)
            result.add(
                    availability(
                            "EQUIPMENT",
                            row.get("equipmentId"),
                            row.get("equipmentName"),
                            decimal(row.get("runningMinutes")),
                            planned));
        var lines =
                source.stream()
                        .collect(
                                Collectors.groupingBy(
                                        row -> ((Number) row.get("lineId")).longValue()));
        lines.forEach(
                (id, rows) -> {
                    var line = db.one("SELECT name FROM line WHERE line_id=?", id);
                    BigDecimal run =
                            rows.stream()
                                    .map(row -> decimal(row.get("runningMinutes")))
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                    result.add(
                            availability(
                                    "LINE",
                                    id,
                                    line.get("name"),
                                    run,
                                    planned.multiply(BigDecimal.valueOf(rows.size()))));
                });
        return ok(AvailabilityStatisticsResponse.class, result);
    }

    private Map<String, Object> availability(
            String type, Object id, Object name, BigDecimal operating, BigDecimal planned) {
        return map(
                "targetType",
                type,
                "targetId",
                id,
                "targetName",
                name,
                "availabilityRate",
                operating
                        .multiply(BigDecimal.valueOf(100))
                        .divide(planned, 2, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(100)),
                "operatingMinutes",
                operating,
                "plannedMinutes",
                planned);
    }

    @Override
    public EquipmentRankingResponse getEquipmentRankings(GetEquipmentRankingsQuery q) {
        var rows = stats(q.from(), q.to(), q.lineId());
        var data = new ArrayList<Map<String, Object>>();
        for (var d : durations(q.from(), q.to(), q.lineId())) {
            var matching =
                    rows.stream()
                            .filter(
                                    r ->
                                            number(r, "equipment_id")
                                                            == ((Number) d.get("equipmentId"))
                                                                    .longValue()
                                                    && "CRITICAL".equals(string(r, "grade")))
                            .toList();
            double repair =
                    matching.stream()
                            .filter(r -> r.get("closed_at") != null)
                            .mapToDouble(
                                    r ->
                                            minutes(
                                                            dateTime(r.get("occurred_at")),
                                                            dateTime(r.get("closed_at")))
                                                    .doubleValue())
                            .average()
                            .orElse(0);
            data.add(
                    map(
                            "equipmentId",
                            d.get("equipmentId"),
                            "equipmentName",
                            d.get("equipmentName"),
                            "faultCount",
                            matching.size(),
                            "downtimeMinutes",
                            d.get("faultMinutes"),
                            "averageRepairMinutes",
                            repair,
                            "lossAmount",
                            actualLoss(
                                            q.from(),
                                            q.to(),
                                            q.lineId(),
                                            ((Number) d.get("equipmentId")).longValue())
                                    .get("lossAmount")));
        }
        String sort = q.sortBy().name();
        data.sort(
                Comparator.<Map<String, Object>, BigDecimal>comparing(r -> decimal(r.get(sort)))
                        .reversed()
                        .thenComparingLong(r -> ((Number) r.get("equipmentId")).longValue()));
        for (int i = 0; i < data.size(); i++) data.get(i).put("rank", i + 1);
        return ok(EquipmentRankingResponse.class, data);
    }

    private List<Map<String, Object>> stops() {
        return db.rows(
                "SELECT s.*,e.name AS equipment_name,p.line_id FROM assembly_stop s LEFT JOIN"
                    + " equipment e ON e.equipment_id=s.cause_equipment_id LEFT JOIN process p ON"
                    + " p.process_id=e.process_id ORDER BY s.started_at");
    }

    private Map<String, Object> actualLoss(
            OffsetDateTime from, OffsetDateTime to, Long lineId, Long equipmentId) {
        BigDecimal amount = BigDecimal.ZERO, stopped = BigDecimal.ZERO;
        Long cause = null;
        String name = null;
        for (var row : stops()) {
            long line =
                    row.get("line_id") == null
                            ? number(row, "shortage_line_id")
                            : number(row, "line_id");
            if (!matches(lineId, line) || !matches(equipmentId, number(row, "cause_equipment_id")))
                continue;
            var originalStart = dateTime(row.get("started_at"));
            var originalEnd =
                    row.get("ended_at") == null ? db.now() : dateTime(row.get("ended_at"));
            var start = originalStart.isBefore(from) ? from : originalStart;
            var end = originalEnd.isAfter(to) ? to : originalEnd;
            if (!end.isAfter(start)) continue;
            long duration = seconds(start, end);
            stopped = stopped.add(minutes(start, end));
            if (row.get("loss_amount") != null && seconds(originalStart, originalEnd) > 0)
                amount =
                        amount.add(
                                decimal(row.get("loss_amount"))
                                        .multiply(BigDecimal.valueOf(duration))
                                        .divide(
                                                BigDecimal.valueOf(
                                                        seconds(originalStart, originalEnd)),
                                                0,
                                                RoundingMode.HALF_UP));
            else
                amount =
                        amount.add(
                                BigDecimal.valueOf(duration)
                                        .multiply(decimal(row.get("hourly_output")))
                                        .multiply(decimal(row.get("unit_price")))
                                        .divide(BigDecimal.valueOf(3600), 0, RoundingMode.HALF_UP));
            if (row.get("cause_equipment_id") != null) {
                cause = number(row, "cause_equipment_id");
                name = string(row, "equipment_name");
            }
        }
        return map(
                "lossAmount",
                amount,
                "assemblyStopMinutes",
                stopped,
                "causeEquipmentId",
                cause,
                "causeEquipmentName",
                name);
    }

    @Override
    public LossStatisticsResponse getLossStatistics(GetLossStatisticsQuery q) {
        var days = new ArrayList<Map<String, Object>>();
        for (LocalDate date = q.from().toLocalDate();
                !date.isAfter(q.to().toLocalDate());
                date = date.plusDays(1)) {
            var start = date.atStartOfDay(db.clock.getZone()).toOffsetDateTime();
            var end = start.plusDays(1);
            if (start.isBefore(q.from())) start = q.from();
            if (end.isAfter(q.to())) end = q.to();
            var result = actualLoss(start, end, q.lineId(), null);
            result.put("date", date);
            days.add(result);
        }
        var today = db.now().toLocalDate().atStartOfDay(db.clock.getZone()).toOffsetDateTime();
        return ok(
                LossStatisticsResponse.class,
                map(
                        "todayLossAmount",
                        actualLoss(today, today.plusDays(1), q.lineId(), null).get("lossAmount"),
                        "weeklyLossAmount",
                        actualLoss(today.minusDays(6), today.plusDays(1), q.lineId(), null)
                                .get("lossAmount"),
                        "daily",
                        days));
    }

    @Override
    public LossAvoidanceResponse getLossAvoidanceStatistics(GetLossAvoidanceStatisticsQuery q) {
        return new JdbcBForecasts(db, inventory, settings).avoidance();
    }
}
