package com.sfaasproject.stf.operations.port;

import com.sfaasproject.stf.operations.dto.BContract.*;

import java.time.*;
import java.util.List;

/** Read projections; implement with MySQL/Redis query adapters. No fabricated fallback data. */
public interface BReadPort {
    public record GetEquipmentLayoutQuery(Long lineId) {}

    public record GetAssemblyStopForecastQuery() {}

    public record GetInventoryQuery() {}

    public record GetInventoryHistoryQuery(
            OffsetDateTime from, OffsetDateTime to, List<Long> lineIds) {}

    public record GetInventoryDetailQuery(Long lineId) {}

    public record GetActiveAlarmsQuery(
            Long lineId,
            Long equipmentId,
            AlarmProcessStatus processStatus,
            AlarmGrade grade,
            Integer page,
            Integer size,
            String sort) {}

    public record GetRecentClosedAlarmsQuery(Integer limit) {}

    public record GetAlarmDetailQuery(Long alarmId) {}

    public record GetAlarmComparisonQuery(Long alarmId) {}

    public record GetRecommendedActionsQuery(Long alarmId) {}

    public record GetAlarmEffectQuery(Long alarmId) {}

    public record GetAlarmHistoriesQuery(
            OffsetDateTime from,
            OffsetDateTime to,
            Long lineId,
            Long equipmentId,
            AlarmGrade grade,
            AlarmResult result,
            Integer page,
            Integer size,
            String sort) {}

    public record GetEquipmentFaultHistoriesQuery(
            Long equipmentId, OffsetDateTime from, OffsetDateTime to) {}

    public record GetEquipmentPartsQuery(Long equipmentId) {}

    public record GetPartUsageQuery(
            Long lineId,
            Long processId,
            Long equipmentId,
            PartUsageStatus status,
            Integer page,
            Integer size) {}

    public record GetPartReplacementHistoriesQuery(
            Long equipmentId, Long partId, OffsetDateTime from, OffsetDateTime to) {}

    public record GetAlarmResponseStatisticsQuery(
            OffsetDateTime from, OffsetDateTime to, Long lineId) {}

    public record GetFalsePositiveStatisticsQuery(
            OffsetDateTime from, OffsetDateTime to, Long lineId) {}

    public record GetDailyAlarmStatisticsQuery(
            OffsetDateTime from, OffsetDateTime to, Long lineId) {}

    public record GetAvailabilityStatisticsQuery(
            OffsetDateTime from, OffsetDateTime to, Long lineId) {}

    public record GetDowntimeStatisticsQuery(OffsetDateTime from, OffsetDateTime to, Long lineId) {}

    public record GetEquipmentRankingsQuery(
            OffsetDateTime from,
            OffsetDateTime to,
            Long lineId,
            GetEquipmentRankingsSortBy sortBy) {}

    public record GetLossStatisticsQuery(OffsetDateTime from, OffsetDateTime to, Long lineId) {}

    public record GetLossAvoidanceStatisticsQuery() {}

    EquipmentLayoutResponse getEquipmentLayout(GetEquipmentLayoutQuery query);

    AssemblyStopForecastResponse getAssemblyStopForecast(GetAssemblyStopForecastQuery query);

    InventoryResponse getInventory(GetInventoryQuery query);

    InventoryHistoryResponse getInventoryHistory(GetInventoryHistoryQuery query);

    InventoryDetailResponse getInventoryDetail(GetInventoryDetailQuery query);

    AlarmPageResponse getActiveAlarms(GetActiveAlarmsQuery query);

    RecentClosedAlarmResponse getRecentClosedAlarms(GetRecentClosedAlarmsQuery query);

    AlarmDetailResponse getAlarmDetail(GetAlarmDetailQuery query);

    AlarmComparisonResponse getAlarmComparison(GetAlarmComparisonQuery query);

    RecommendedActionResponse getRecommendedActions(GetRecommendedActionsQuery query);

    AlarmEffectResponse getAlarmEffect(GetAlarmEffectQuery query);

    AlarmHistoryPageResponse getAlarmHistories(GetAlarmHistoriesQuery query);

    FaultHistoryResponse getEquipmentFaultHistories(GetEquipmentFaultHistoriesQuery query);

    InstalledPartListResponse getEquipmentParts(GetEquipmentPartsQuery query);

    PartUsagePageResponse getPartUsage(GetPartUsageQuery query);

    PartReplacementHistoryResponse getPartReplacementHistories(
            GetPartReplacementHistoriesQuery query);

    AlarmResponseStatisticsResponse getAlarmResponseStatistics(
            GetAlarmResponseStatisticsQuery query);

    FalsePositiveStatisticsResponse getFalsePositiveStatistics(
            GetFalsePositiveStatisticsQuery query);

    DailyAlarmStatisticsResponse getDailyAlarmStatistics(GetDailyAlarmStatisticsQuery query);

    AvailabilityStatisticsResponse getAvailabilityStatistics(GetAvailabilityStatisticsQuery query);

    DowntimeStatisticsResponse getDowntimeStatistics(GetDowntimeStatisticsQuery query);

    EquipmentRankingResponse getEquipmentRankings(GetEquipmentRankingsQuery query);

    LossStatisticsResponse getLossStatistics(GetLossStatisticsQuery query);

    LossAvoidanceResponse getLossAvoidanceStatistics(GetLossAvoidanceStatisticsQuery query);
}
