package com.sfaasproject.stf.inventory.service;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;
import static com.sfaasproject.stf.operations.service.V7Support.*;

import com.sfaasproject.stf.alarm.service.*;
import com.sfaasproject.stf.operations.adapter.JdbcBDatabase;
import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.operations.port.V7Context;

import java.math.*;
import java.time.*;
import java.util.*;

public class V7InventoryService {
    private final JdbcBDatabase db;
    private final V7Context context;
    private final AssemblyForecastService forecast;
    private final RecoveryTimeEstimator repair;
    private final String source;

    public V7InventoryService(
            JdbcBDatabase db,
            V7Context context,
            AssemblyForecastService forecast,
            RecoveryTimeEstimator repair,
            String source) {
        if (!List.of("DEMO_PRODUCTION", "INVENTORY_SYSTEM").contains(source))
            throw new IllegalArgumentException("Invalid inventory source");
        this.db = db;
        this.context = context;
        this.forecast = forecast;
        this.repair = repair;
        this.source = source;
    }

    public record Snapshot(
            OffsetDateTime calculatedAt,
            List<InventoryLine> inventory,
            List<LineStopForecast> lines) {}

    public Snapshot snapshot() {
        var at = db.now();
        int window = context.integer("RATE_WINDOW_MIN", 10),
                warning = context.integer("STOCK_LEVEL_MIN", 30),
                danger = context.integer("STOCK_DANGER_MIN", 10);
        var hourly = context.decimal("HOURLY_OUTPUT", null);
        var price = context.decimal("UNIT_PRICE", null);
        var thresholds = new InventoryStageThresholds(warning, danger);
        var equipment = context.equipment();
        var lines = context.lines().stream().filter(l -> !l.deleted()).toList();
        var rows =
                db.rows(
                        "SELECT * FROM alarm WHERE process_state='ACTIVE' AND grade='CRITICAL' AND"
                            + " value_state='ABNORMAL' ORDER BY occurred_at,alarm_id");
        var assembly = lines.stream().filter(l -> l.type() == LineType.ASSEMBLY).toList();
        BigDecimal consumption = assembly.isEmpty() ? null : BigDecimal.ZERO;
        String assemblyQuality = assembly.isEmpty() ? "INSUFFICIENT_DATA" : "READY";
        for (var line : assembly) {
            consumption = consumption.add(context.productionRate(line.id(), at, window));
            String q = context.counterQuality(line.id(), at, window);
            if (!"READY".equals(q)) assemblyQuality = q;
            if (equipment.stream()
                    .anyMatch(
                            e ->
                                    !e.deleted()
                                            && e.lineId() == line.id()
                                            && e.status() == EquipmentStatus.COMMUNICATION_LOST))
                assemblyQuality = "INSUFFICIENT_DATA";
        }
        var inv = new ArrayList<InventoryLine>();
        var forecasts = new ArrayList<LineStopForecast>();
        for (var line : lines.stream().filter(l -> l.type() == LineType.PART).toList()) {
            var stock =
                    db.one(
                            "SELECT qty FROM stock_history WHERE line_id=? AND recorded_at<=? ORDER"
                                + " BY recorded_at DESC LIMIT 1",
                            line.id(),
                            time(at));
            Long quantity = stock == null ? null : number(stock, "qty");
            var ids =
                    equipment.stream()
                            .filter(e -> !e.deleted() && e.lineId() == line.id())
                            .map(V7Context.Equipment::id)
                            .toList();
            var fault =
                    rows.stream()
                            .filter(a -> ids.contains(number(a, "equipment_id")))
                            .findFirst()
                            .orElse(null);
            boolean faulted =
                    fault != null
                            || equipment.stream()
                                    .anyMatch(
                                            e ->
                                                    !e.deleted()
                                                            && e.lineId() == line.id()
                                                            && e.status() == EquipmentStatus.FAULT);
            String quality =
                    quantity == null
                            ? "INSUFFICIENT_DATA"
                            : context.counterQuality(line.id(), at, window);
            if (!"READY".equals(assemblyQuality)) quality = assemblyQuality;
            if (equipment.stream()
                    .anyMatch(
                            e ->
                                    !e.deleted()
                                            && e.lineId() == line.id()
                                            && e.status() == EquipmentStatus.COMMUNICATION_LOST))
                quality = "INSUFFICIENT_DATA";
            BigDecimal production =
                    faulted ? BigDecimal.ZERO : context.productionRate(line.id(), at, window);
            if (quantity != null && quantity < 0) {
                quantity = null;
                quality = "COUNTER_INCONSISTENT";
            }
            if (!"READY".equals(quality)) {
                production = null;
            }
            var actualConsumption = "READY".equals(quality) ? consumption : null;
            var estimate = new RecoveryTimeEstimator.Estimate(0, null, null);
            if (fault != null) {
                var history =
                        db.rows(
                                "SELECT * FROM alarm WHERE equipment_id=? AND data_item_id=? AND"
                                    + " process_state='CLOSED' AND grade='CRITICAL' AND closed_at<?"
                                    + " AND closed_at>=occurred_at",
                                fault.get("equipment_id"),
                                fault.get("data_item_id"),
                                fault.get("occurred_at"));
                estimate =
                        repair.estimate(
                                number(fault, "equipment_id"),
                                number(fault, "data_item_id"),
                                dateTime(fault.get("occurred_at")),
                                history.stream()
                                        .map(
                                                h ->
                                                        new RecoveryTimeEstimator.ClosedAlarm(
                                                                number(h, "equipment_id"),
                                                                number(h, "data_item_id"),
                                                                com.sfaasproject.stf.alarm.service
                                                                        .AlarmGrade.CRITICAL,
                                                                dateTime(h.get("occurred_at")),
                                                                dateTime(h.get("closed_at"))))
                                        .toList());
            }
            var f =
                    forecast.calculate(
                            new AssemblyForecastService.Input(
                                    quantity, production, actualConsumption, at, quality),
                            warning,
                            danger,
                            estimate.recoveryExpectedAt(),
                            hourly,
                            price);
            inv.add(
                    dto(
                            com.sfaasproject.stf.operations.service.V7Support.map(
                                    "lineId",
                                    line.id(),
                                    "lineName",
                                    line.name(),
                                    "lineType",
                                    "PART",
                                    "quantity",
                                    quantity,
                                    "productionPerMinute",
                                    production,
                                    "consumptionPerMinute",
                                    actualConsumption,
                                    "calculatedAt",
                                    at,
                                    "calculationStatus",
                                    quality,
                                    "stage",
                                    f.stage(),
                                    "remainingMinutes",
                                    f.remainingMinutes(),
                                    "assemblyStopExpectedAt",
                                    f.stopAt(),
                                    "stageThresholds",
                                    thresholds,
                                    "inventorySource",
                                    source),
                            InventoryLine.class));
            forecasts.add(
                    dto(
                            com.sfaasproject.stf.operations.service.V7Support.map(
                                    "lineId",
                                    line.id(),
                                    "lineName",
                                    line.name(),
                                    "lineType",
                                    "PART",
                                    "currentInventory",
                                    quantity,
                                    "netConsumptionPerMinute",
                                    f.netConsumption(),
                                    "remainingMinutes",
                                    f.remainingMinutes(),
                                    "stage",
                                    f.stage(),
                                    "assemblyStopExpectedAt",
                                    f.stopAt(),
                                    "sourceEquipmentId",
                                    fault == null ? null : fault.get("equipment_id"),
                                    "activeAlarmId",
                                    fault == null ? null : fault.get("alarm_id"),
                                    "recoveryExpectedAt",
                                    estimate.recoveryExpectedAt(),
                                    "repairHistoryCount",
                                    estimate.repairHistoryCount(),
                                    "averageRepairMinutes",
                                    estimate.averageRepairMinutes(),
                                    "marginMinutes",
                                    f.marginMinutes(),
                                    "marginStatus",
                                    f.marginMinutes() == null
                                            ? null
                                            : f.marginMinutes().signum() >= 0
                                                    ? "AVAILABLE"
                                                    : "SHORTAGE",
                                    "shortageMinutes",
                                    f.shortageMinutes(),
                                    "hourlyProduction",
                                    hourly,
                                    "unitPrice",
                                    price,
                                    "unitPriceConfigured",
                                    price != null && price.signum() > 0,
                                    "expectedLossAmount",
                                    f.loss(),
                                    "forecastStatus",
                                    f.forecastStatus(),
                                    "stageThresholds",
                                    thresholds),
                            LineStopForecast.class));
        }
        forecasts.sort(
                Comparator.comparing(
                                LineStopForecast::assemblyStopExpectedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LineStopForecast::lineId));
        return new Snapshot(at, List.copyOf(inv), List.copyOf(forecasts));
    }

    public InventoryResponse inventory() {
        return new InventoryResponse(true, snapshot().inventory());
    }

    public InventoryDetailResponse detail(long line) {
        id(line);
        var r =
                snapshot().inventory().stream()
                        .filter(l -> l.lineId() == line)
                        .findFirst()
                        .orElseThrow(() -> missing("LINE_NOT_FOUND"));
        return dto(
                com.sfaasproject.stf.operations.service.V7Support.map("success", true, "data", r),
                InventoryDetailResponse.class);
    }

    public AssemblyStopForecastResponse overview() {
        var s = snapshot();
        var first =
                s.lines().stream()
                        .filter(l -> l.assemblyStopExpectedAt() != null)
                        .findFirst()
                        .orElse(null);
        return new AssemblyStopForecastResponse(
                true,
                new AssemblyStopForecastData(
                        first == null ? null : first.lineId(),
                        first == null ? null : first.assemblyStopExpectedAt(),
                        s.lines(),
                        s.calculatedAt()));
    }

    public InventoryHistoryResponse history(
            OffsetDateTime from, OffsetDateTime to, List<Long> ids) {
        var p = period(from, to, db.now());
        if (ids != null)
            ids.forEach(
                    x -> {
                        if (x == null)
                            throw com.sfaasproject.stf.operations.service.Checks.invalid(
                                    "lineIds가 필요합니다.");
                        id(x);
                    });
        var lines =
                context.lines().stream()
                        .filter(
                                l ->
                                        l.type() == LineType.PART
                                                && !l.deleted()
                                                && (ids == null
                                                        || ids.isEmpty()
                                                        || ids.contains(l.id())))
                        .collect(java.util.stream.Collectors.toMap(V7Context.Line::id, l -> l));
        var items =
                db
                        .rows(
                                "SELECT * FROM stock_history WHERE recorded_at>=? AND recorded_at<?"
                                    + " ORDER BY recorded_at,line_id",
                                time(p.from()),
                                time(p.to()))
                        .stream()
                        .filter(r -> lines.containsKey(number(r, "line_id")))
                        .map(
                                r ->
                                        new InventoryHistoryPoint(
                                                dateTime(r.get("recorded_at")),
                                                number(r, "line_id"),
                                                lines.get(number(r, "line_id")).name(),
                                                number(r, "qty")))
                        .toList();
        return new InventoryHistoryResponse(true, items);
    }
}
