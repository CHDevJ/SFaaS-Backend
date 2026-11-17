package com.sfaasproject.stf.operations.service;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BReadPort;
import com.sfaasproject.stf.operations.port.BReadPort.*;

public class BReadService {
    private final BReadPort port;

    public BReadService(BReadPort port) {
        this.port = port;
    }

    public EquipmentLayoutResponse getEquipmentLayout(GetEquipmentLayoutQuery q) {
        Checks.positive(q.lineId(), "lineId");
        return java.util.Objects.requireNonNull(
                port.getEquipmentLayout(q), "Read adapter returned null");
    }

    public AssemblyStopForecastResponse getAssemblyStopForecast(GetAssemblyStopForecastQuery q) {

        return java.util.Objects.requireNonNull(
                port.getAssemblyStopForecast(q), "Read adapter returned null");
    }

    public InventoryResponse getInventory(GetInventoryQuery q) {

        return java.util.Objects.requireNonNull(port.getInventory(q), "Read adapter returned null");
    }

    public InventoryHistoryResponse getInventoryHistory(GetInventoryHistoryQuery q) {
        Checks.required(q.from(), "from");
        Checks.required(q.to(), "to");
        Checks.period(q.from(), q.to());
        if (q.lineIds() != null)
            q.lineIds()
                    .forEach(
                            id -> {
                                Checks.required(id, "lineIds");
                                Checks.positive(id, "lineIds");
                            });
        return java.util.Objects.requireNonNull(
                port.getInventoryHistory(q), "Read adapter returned null");
    }

    public InventoryDetailResponse getInventoryDetail(GetInventoryDetailQuery q) {
        Checks.required(q.lineId(), "lineId");
        Checks.positive(q.lineId(), "lineId");
        return java.util.Objects.requireNonNull(
                port.getInventoryDetail(q), "Read adapter returned null");
    }

    public AlarmPageResponse getActiveAlarms(GetActiveAlarmsQuery q) {
        Checks.positive(q.lineId(), "lineId");
        Checks.positive(q.equipmentId(), "equipmentId");
        Checks.range(q.page(), 0, 2147483647, "page");
        Checks.range(q.size(), 1, 100, "size");
        Checks.sort(q.sort());
        return java.util.Objects.requireNonNull(
                port.getActiveAlarms(q), "Read adapter returned null");
    }

    public RecentClosedAlarmResponse getRecentClosedAlarms(GetRecentClosedAlarmsQuery q) {
        Checks.range(q.limit(), 1, 100, "limit");
        return java.util.Objects.requireNonNull(
                port.getRecentClosedAlarms(q), "Read adapter returned null");
    }

    public AlarmDetailResponse getAlarmDetail(GetAlarmDetailQuery q) {
        Checks.required(q.alarmId(), "alarmId");
        Checks.positive(q.alarmId(), "alarmId");
        return java.util.Objects.requireNonNull(
                port.getAlarmDetail(q), "Read adapter returned null");
    }

    public AlarmComparisonResponse getAlarmComparison(GetAlarmComparisonQuery q) {
        Checks.required(q.alarmId(), "alarmId");
        Checks.positive(q.alarmId(), "alarmId");
        return java.util.Objects.requireNonNull(
                port.getAlarmComparison(q), "Read adapter returned null");
    }

    public RecommendedActionResponse getRecommendedActions(GetRecommendedActionsQuery q) {
        Checks.required(q.alarmId(), "alarmId");
        Checks.positive(q.alarmId(), "alarmId");
        return java.util.Objects.requireNonNull(
                port.getRecommendedActions(q), "Read adapter returned null");
    }

    public AlarmEffectResponse getAlarmEffect(GetAlarmEffectQuery q) {
        Checks.required(q.alarmId(), "alarmId");
        Checks.positive(q.alarmId(), "alarmId");
        return java.util.Objects.requireNonNull(
                port.getAlarmEffect(q), "Read adapter returned null");
    }

    public AlarmHistoryPageResponse getAlarmHistories(GetAlarmHistoriesQuery q) {
        Checks.required(q.from(), "from");
        Checks.required(q.to(), "to");
        Checks.positive(q.lineId(), "lineId");
        Checks.positive(q.equipmentId(), "equipmentId");
        Checks.range(q.page(), 0, 2147483647, "page");
        Checks.range(q.size(), 1, 100, "size");
        Checks.period(q.from(), q.to());
        Checks.sort(q.sort());
        return java.util.Objects.requireNonNull(
                port.getAlarmHistories(q), "Read adapter returned null");
    }

    public FaultHistoryResponse getEquipmentFaultHistories(GetEquipmentFaultHistoriesQuery q) {
        Checks.required(q.equipmentId(), "equipmentId");
        Checks.positive(q.equipmentId(), "equipmentId");
        Checks.period(q.from(), q.to());
        return java.util.Objects.requireNonNull(
                port.getEquipmentFaultHistories(q), "Read adapter returned null");
    }

    public InstalledPartListResponse getEquipmentParts(GetEquipmentPartsQuery q) {
        Checks.required(q.equipmentId(), "equipmentId");
        Checks.positive(q.equipmentId(), "equipmentId");
        return java.util.Objects.requireNonNull(
                port.getEquipmentParts(q), "Read adapter returned null");
    }

    public PartUsagePageResponse getPartUsage(GetPartUsageQuery q) {
        Checks.positive(q.lineId(), "lineId");
        Checks.positive(q.processId(), "processId");
        Checks.positive(q.equipmentId(), "equipmentId");
        Checks.range(q.page(), 0, 2147483647, "page");
        Checks.range(q.size(), 1, 100, "size");
        return java.util.Objects.requireNonNull(port.getPartUsage(q), "Read adapter returned null");
    }

    public PartReplacementHistoryResponse getPartReplacementHistories(
            GetPartReplacementHistoriesQuery q) {
        Checks.required(q.equipmentId(), "equipmentId");
        Checks.positive(q.equipmentId(), "equipmentId");
        Checks.required(q.partId(), "partId");
        Checks.positive(q.partId(), "partId");
        Checks.period(q.from(), q.to());
        return java.util.Objects.requireNonNull(
                port.getPartReplacementHistories(q), "Read adapter returned null");
    }

    public AlarmResponseStatisticsResponse getAlarmResponseStatistics(
            GetAlarmResponseStatisticsQuery q) {
        Checks.required(q.from(), "from");
        Checks.required(q.to(), "to");
        Checks.positive(q.lineId(), "lineId");
        Checks.period(q.from(), q.to());
        return java.util.Objects.requireNonNull(
                port.getAlarmResponseStatistics(q), "Read adapter returned null");
    }

    public FalsePositiveStatisticsResponse getFalsePositiveStatistics(
            GetFalsePositiveStatisticsQuery q) {
        Checks.required(q.from(), "from");
        Checks.required(q.to(), "to");
        Checks.positive(q.lineId(), "lineId");
        Checks.period(q.from(), q.to());
        return java.util.Objects.requireNonNull(
                port.getFalsePositiveStatistics(q), "Read adapter returned null");
    }

    public DailyAlarmStatisticsResponse getDailyAlarmStatistics(GetDailyAlarmStatisticsQuery q) {
        Checks.required(q.from(), "from");
        Checks.required(q.to(), "to");
        Checks.positive(q.lineId(), "lineId");
        Checks.period(q.from(), q.to());
        return java.util.Objects.requireNonNull(
                port.getDailyAlarmStatistics(q), "Read adapter returned null");
    }

    public AvailabilityStatisticsResponse getAvailabilityStatistics(
            GetAvailabilityStatisticsQuery q) {
        Checks.required(q.from(), "from");
        Checks.required(q.to(), "to");
        Checks.positive(q.lineId(), "lineId");
        Checks.period(q.from(), q.to());
        return java.util.Objects.requireNonNull(
                port.getAvailabilityStatistics(q), "Read adapter returned null");
    }

    public DowntimeStatisticsResponse getDowntimeStatistics(GetDowntimeStatisticsQuery q) {
        Checks.required(q.from(), "from");
        Checks.required(q.to(), "to");
        Checks.positive(q.lineId(), "lineId");
        Checks.period(q.from(), q.to());
        return java.util.Objects.requireNonNull(
                port.getDowntimeStatistics(q), "Read adapter returned null");
    }

    public EquipmentRankingResponse getEquipmentRankings(GetEquipmentRankingsQuery q) {
        Checks.required(q.from(), "from");
        Checks.required(q.to(), "to");
        Checks.positive(q.lineId(), "lineId");
        Checks.required(q.sortBy(), "sortBy");
        Checks.period(q.from(), q.to());
        return java.util.Objects.requireNonNull(
                port.getEquipmentRankings(q), "Read adapter returned null");
    }

    public LossStatisticsResponse getLossStatistics(GetLossStatisticsQuery q) {
        Checks.required(q.from(), "from");
        Checks.required(q.to(), "to");
        Checks.positive(q.lineId(), "lineId");
        Checks.period(q.from(), q.to());
        return java.util.Objects.requireNonNull(
                port.getLossStatistics(q), "Read adapter returned null");
    }

    public LossAvoidanceResponse getLossAvoidanceStatistics(GetLossAvoidanceStatisticsQuery q) {

        return java.util.Objects.requireNonNull(
                port.getLossAvoidanceStatistics(q), "Read adapter returned null");
    }
}
