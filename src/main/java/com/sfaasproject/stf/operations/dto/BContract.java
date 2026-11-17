package com.sfaasproject.stf.operations.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Transport types derived from docs/openapi.yaml v5; no persistence entities. */
public final class BContract {
    private BContract() {}

    public record EquipmentLayoutResponse(
            @NotNull Boolean success, @NotNull @Valid EquipmentLayoutData data) {}

    public record EquipmentLayoutData(
            @NotNull @Valid List<EquipmentStatusCount> statusCounts,
            @NotNull @Valid List<EquipmentSummary> equipment) {}

    public record EquipmentStatusCount(
            @NotNull @Valid EquipmentStatus status,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer count) {}

    public enum EquipmentStatus {
        RUNNING,
        IDLE,
        CAUTION,
        FAULT,
        COMMUNICATION_LOST
    }

    public record EquipmentSummary(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long equipmentId,
            @NotNull @Size(max = 100) String equipmentCode,
            @NotNull @Size(max = 200) String equipmentName,
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long lineId,
            @NotNull String lineName,
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long processId,
            @NotNull String processName,
            @NotNull @DecimalMin(value = "1", inclusive = true) Integer processOrder,
            @NotNull @Valid EquipmentStatus status,
            @NotNull OffsetDateTime statusStartedAt,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer activeAlarmCount,
            @Valid List<CurrentDataItem> mainMeasurements,
            @Valid List<AbnormalMeasurement> abnormalMeasurements,
            @Valid List<DuePartSummary> dueParts) {}

    public record CurrentDataItem(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long dataItemId,
            @NotNull @Size(max = 100) String dataItemName,
            @NotNull @Valid DataValueType valueType,
            @NotNull Object value,
            String displayValue,
            @Size(max = 30) String unit,
            CurrentDataItemValueStatus valueStatus,
            BigDecimal cautionThreshold,
            BigDecimal criticalThreshold,
            @NotNull OffsetDateTime measuredAt) {}

    public enum DataValueType {
        MEASUREMENT,
        STATE,
        CUMULATIVE
    }

    public enum CurrentDataItemValueStatus {
        NORMAL,
        CAUTION,
        CRITICAL
    }

    public record AbnormalMeasurement(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long dataItemId,
            @NotNull String dataItemName,
            @NotNull BigDecimal currentValue,
            String unit,
            @NotNull AbnormalMeasurementValueStatus valueStatus,
            BigDecimal threshold) {}

    public enum AbnormalMeasurementValueStatus {
        CAUTION,
        CRITICAL
    }

    public record DuePartSummary(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long partId,
            @NotNull String partName,
            @NotNull @Valid PartUsageStatus status,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal usageRate) {}

    public enum PartUsageStatus {
        NORMAL,
        DUE_SOON,
        OVERDUE
    }

    public record AssemblyStopForecastResponse(
            @NotNull Boolean success, @NotNull @Valid AssemblyStopForecastData data) {}

    public record AssemblyStopForecastData(
            Long representativeLineId,
            OffsetDateTime earliestAssemblyStopExpectedAt,
            @NotNull @Valid List<LineStopForecast> lines) {}

    public record LineStopForecast(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long lineId,
            @NotNull String lineName,
            @NotNull @DecimalMin(value = "0", inclusive = true) Long currentInventory,
            BigDecimal netConsumptionPerMinute,
            @DecimalMin(value = "0", inclusive = true) Integer remainingMinutes,
            @NotNull @Valid InventoryStage stage,
            OffsetDateTime assemblyStopExpectedAt,
            Long sourceEquipmentId,
            Long activeAlarmId,
            OffsetDateTime recoveryExpectedAt,
            Integer marginMinutes,
            LineStopForecastMarginStatus marginStatus,
            @DecimalMin(value = "0", inclusive = true) BigDecimal hourlyProduction,
            @DecimalMin(value = "0", inclusive = true) BigDecimal unitPrice,
            @DecimalMin(value = "0", inclusive = true) BigDecimal expectedLossAmount) {}

    public enum InventoryStage {
        MAINTAIN,
        CAUTION,
        DANGER
    }

    public enum LineStopForecastMarginStatus {
        AVAILABLE,
        SHORTAGE
    }

    public record InventoryResponse(
            @NotNull Boolean success, @NotNull @Valid List<InventoryLine> data) {}

    public record InventoryLine(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long lineId,
            @NotNull String lineName,
            @NotNull @DecimalMin(value = "0", inclusive = true) Long quantity,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal consumptionPerMinute,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal productionPerMinute,
            OffsetDateTime calculatedAt) {}

    public record InventoryHistoryResponse(
            @NotNull Boolean success, @NotNull @Valid List<InventoryHistoryPoint> data) {}

    public record InventoryHistoryPoint(
            @NotNull OffsetDateTime recordedAt,
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long lineId,
            String lineName,
            @NotNull @DecimalMin(value = "0", inclusive = true) Long quantity) {}

    public record InventoryDetailResponse(
            @NotNull Boolean success, @NotNull @Valid InventoryDetail data) {}

    public record InventoryDetail(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long lineId,
            @NotNull String lineName,
            @NotNull @DecimalMin(value = "0", inclusive = true) Long quantity,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal consumptionPerMinute,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal productionPerMinute,
            OffsetDateTime calculatedAt,
            @NotNull @Valid InventoryStage stage,
            BigDecimal netConsumptionPerMinute,
            @DecimalMin(value = "0", inclusive = true) Integer remainingMinutes,
            OffsetDateTime expectedDepletionAt,
            OffsetDateTime expectedRecoveryAt,
            Long activeAlarmId,
            Long sourceEquipmentId) {}

    public record AlarmPageResponse(
            @NotNull Boolean success,
            @NotNull @Valid AlarmPageData data,
            @NotNull @Valid PageMeta meta) {}

    public record AlarmPageData(
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer activeCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer unacknowledgedCount,
            @NotNull @Valid List<AlarmSummary> items) {}

    public record AlarmSummary(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long alarmId,
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long equipmentId,
            String equipmentCode,
            @NotNull String equipmentName,
            @DecimalMin(value = "1", inclusive = true) @Valid Long lineId,
            String lineName,
            @DecimalMin(value = "1", inclusive = true) @Valid Long dataItemId,
            String dataItemName,
            @NotNull String alarmMessage,
            Object currentValue,
            String unit,
            BigDecimal thresholdValue,
            @NotNull @Valid AlarmGrade grade,
            @NotNull @Valid AlarmValueStatus valueStatus,
            @NotNull @Valid AlarmProcessStatus processStatus,
            @Valid AlarmSummaryAssignee assignee,
            @NotNull OffsetDateTime occurredAt,
            @DecimalMin(value = "0", inclusive = true) Long elapsedSeconds,
            Boolean acknowledgementDelayed) {}

    public enum AlarmGrade {
        CAUTION,
        CRITICAL
    }

    public enum AlarmValueStatus {
        ABNORMAL,
        RECOVERED
    }

    public enum AlarmProcessStatus {
        UNACKNOWLEDGED,
        ACKNOWLEDGED,
        ACTION,
        CLOSED
    }

    public record AlarmSummaryAssignee(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long userId,
            @NotNull @Size(max = 100) String name,
            @NotNull @Valid @com.fasterxml.jackson.annotation.JsonProperty("permission") @com.fasterxml.jackson.annotation.JsonAlias("position") Position position) {}

    public enum Position {
        ADMIN,
        USER
    }

    public record PageMeta(
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer page,
            @NotNull @DecimalMin(value = "1", inclusive = true) Integer size,
            @NotNull @DecimalMin(value = "0", inclusive = true) Long totalElements,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer totalPages) {}

    public record RecentClosedAlarmResponse(
            @NotNull Boolean success, @NotNull @Valid List<RecentClosedAlarm> data) {}

    public record RecentClosedAlarm(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long alarmId,
            @NotNull OffsetDateTime closedAt,
            @NotNull String equipmentName,
            @NotNull String alarmMessage,
            @NotNull @DecimalMin(value = "0", inclusive = true) Long durationSeconds,
            @NotNull @Valid AlarmResult result,
            @Valid RecentClosedAlarmAcknowledger acknowledger) {}

    public enum AlarmResult {
        EFFECTIVE,
        FALSE_POSITIVE
    }

    public record RecentClosedAlarmAcknowledger(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long userId,
            @NotNull @Size(max = 100) String name,
            @NotNull @Valid @com.fasterxml.jackson.annotation.JsonProperty("permission") @com.fasterxml.jackson.annotation.JsonAlias("position") Position position) {}

    public record AlarmDetailResponse(
            @NotNull Boolean success, @NotNull @Valid AlarmDetailData data) {}

    public record AlarmDetailData(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long alarmId,
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long equipmentId,
            String equipmentCode,
            @NotNull String equipmentName,
            @DecimalMin(value = "1", inclusive = true) @Valid Long lineId,
            String lineName,
            @DecimalMin(value = "1", inclusive = true) @Valid Long dataItemId,
            String dataItemName,
            @NotNull String alarmMessage,
            Object currentValue,
            String unit,
            BigDecimal thresholdValue,
            @NotNull @Valid AlarmGrade grade,
            @NotNull @Valid AlarmValueStatus valueStatus,
            @NotNull @Valid AlarmProcessStatus processStatus,
            @Valid AlarmDetailDataAssignee assignee,
            @NotNull OffsetDateTime occurredAt,
            @DecimalMin(value = "0", inclusive = true) Long elapsedSeconds,
            Boolean acknowledgementDelayed,
            OffsetDateTime acknowledgedAt,
            OffsetDateTime actionedAt,
            OffsetDateTime recoveredAt,
            OffsetDateTime closedAt,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer recurrenceCount,
            AlarmDetailDataResult result,
            @NotNull @Valid List<MeasurementPoint> trendBeforeAndAfter,
            @NotNull @Valid List<AlarmTimelineEntry> timeline) {}

    public record AlarmDetailDataAssignee(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long userId,
            @NotNull @Size(max = 100) String name,
            @NotNull @Valid @com.fasterxml.jackson.annotation.JsonProperty("permission") @com.fasterxml.jackson.annotation.JsonAlias("position") Position position) {}

    public enum AlarmDetailDataResult {
        EFFECTIVE,
        FALSE_POSITIVE
    }

    public record MeasurementPoint(@NotNull OffsetDateTime measuredAt, @NotNull Object value) {}

    public record AlarmTimelineEntry(
            @NotNull OffsetDateTime recordedAt,
            @Valid AlarmTimelineEntryActor actor,
            @NotNull String actionType,
            @NotNull String content) {}

    public record AlarmTimelineEntryActor(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long userId,
            @NotNull @Size(max = 100) String name,
            @NotNull @Valid @com.fasterxml.jackson.annotation.JsonProperty("permission") @com.fasterxml.jackson.annotation.JsonAlias("position") Position position) {}

    public record AlarmComparisonResponse(
            @NotNull Boolean success, @NotNull @Valid AlarmComparisonData data) {}

    public record AlarmComparisonData(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long alarmId,
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long equipmentId,
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long dataItemId,
            String dataItemName,
            String unit,
            @NotNull OffsetDateTime occurredAt,
            BigDecimal cautionThreshold,
            BigDecimal criticalThreshold,
            BigDecimal beforeAverage,
            BigDecimal afterAverage,
            BigDecimal changeRatePercent,
            @NotNull @Valid List<MeasurementPoint> before,
            @NotNull @Valid List<MeasurementPoint> after) {}

    public record AlarmAcknowledgementResponse(
            @NotNull Boolean success, @NotNull @Valid AlarmAcknowledgementData data) {}

    public record AlarmAcknowledgementData(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long alarmId,
            @NotNull @Valid AlarmProcessStatus processStatus,
            @NotNull @Valid UserBrief acknowledgedBy,
            @NotNull OffsetDateTime acknowledgedAt,
            @NotNull @Valid UserBrief assignee) {}

    public record UserBrief(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long userId,
            @NotNull @Size(max = 100) String name,
            @NotNull @Valid @com.fasterxml.jackson.annotation.JsonProperty("permission") @com.fasterxml.jackson.annotation.JsonAlias("position") Position position) {}

    public record AlarmAcknowledgementRequest(
            @NotNull @DecimalMin(value = "1", inclusive = true) Long assigneeUserId) {}

    public record RecommendedActionResponse(
            @NotNull Boolean success, @NotNull @Valid List<RecommendedAction> data) {}

    public record RecommendedAction(
            @NotNull String actionContent,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer effectiveCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer totalCount,
            @DecimalMin(value = "0", inclusive = true) @DecimalMax("100")
                    BigDecimal effectivenessRate,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal averageRepairMinutes) {}

    public record AlarmActionResponse(
            @NotNull Boolean success, @NotNull @Valid AlarmActionData data) {}

    public record AlarmActionData(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long alarmId,
            @NotNull @Valid AlarmProcessStatus processStatus,
            @NotNull OffsetDateTime actionedAt,
            @NotNull @Valid UserBrief actionedBy,
            Long partReplacementId) {}

    public record AlarmActionRequest(
            @NotNull @NotBlank @Size(min = 1, max = 1000) String cause,
            @NotNull @NotBlank @Size(min = 1, max = 4000) String actionContent,
            Long replacedPartId,
            OffsetDateTime replacedAt,
            @Size(max = 1000) String replacementReason) {}

    public record AlarmClosureResponse(
            @NotNull Boolean success, @NotNull @Valid AlarmClosureData data) {}

    public record AlarmClosureData(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long alarmId,
            @NotNull @Valid AlarmProcessStatus processStatus,
            @NotNull @Valid AlarmResult result,
            @NotNull OffsetDateTime closedAt) {}

    public record FalsePositiveRequest(
            @NotNull @Valid FalsePositiveReason reason, @Size(max = 2000) String detail) {}

    public enum FalsePositiveReason {
        SENSOR_MALFUNCTION,
        THRESHOLD_MISMATCH,
        INSPECTION_OR_TRIAL,
        OTHER
    }

    public record AlarmEffectResponse(
            @NotNull Boolean success, @NotNull @Valid AlarmEffectData data) {}

    public record AlarmEffectData(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long alarmId,
            @NotNull @Valid AlarmProcessStatus processStatus,
            @NotNull @Valid EffectStatus effectStatus,
            OffsetDateTime startedAt,
            @NotNull @DecimalMin(value = "0", inclusive = true) Long currentProductionCount,
            @NotNull @DecimalMin(value = "1", inclusive = true) Long targetProductionCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer recurrenceCount) {}

    public enum EffectStatus {
        WAITING_FOR_RECOVERY,
        IN_PROGRESS,
        PASSED,
        RECURRED
    }

    public record AlarmHistoryPageResponse(
            @NotNull Boolean success,
            @NotNull @Valid List<AlarmHistoryItem> data,
            @NotNull @Valid PageMeta meta) {}

    public record AlarmHistoryItem(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long alarmId,
            @NotNull OffsetDateTime occurredAt,
            @NotNull OffsetDateTime closedAt,
            @NotNull String equipmentName,
            @NotNull String alarmMessage,
            @NotNull @Valid AlarmGrade grade,
            @NotNull @Valid AlarmResult result,
            @NotNull @DecimalMin(value = "0", inclusive = true) Long acknowledgementSeconds,
            @NotNull @DecimalMin(value = "0", inclusive = true) Long repairSeconds,
            @Valid AlarmHistoryItemAssignee assignee) {}

    public record AlarmHistoryItemAssignee(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long userId,
            @NotNull @Size(max = 100) String name,
            @NotNull @Valid @com.fasterxml.jackson.annotation.JsonProperty("permission") @com.fasterxml.jackson.annotation.JsonAlias("position") Position position) {}

    public record FaultHistoryResponse(
            @NotNull Boolean success, @NotNull @Valid FaultHistoryData data) {}

    public record FaultHistoryData(
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer monthlyFaultCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal averageRepairMinutes,
            @NotNull @DecimalMin(value = "0", inclusive = true)
                    BigDecimal averageFailureIntervalMinutes,
            @NotNull @Valid List<FaultHistoryItem> items) {}

    public record FaultHistoryItem(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long alarmId,
            @NotNull OffsetDateTime occurredAt,
            @NotNull OffsetDateTime closedAt,
            @NotNull String cause,
            @NotNull String actionContent,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal repairMinutes,
            @NotNull @Valid AlarmResult result) {}

    public record InstalledPartListResponse(
            @NotNull Boolean success, @NotNull @Valid List<InstalledPart> data) {}

    public record InstalledPart(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long partId,
            @NotNull String partName,
            String specification,
            String managementNumber,
            @NotNull @Valid PartLifeBasis lifeBasis,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal usage,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal replacementCriterion,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal usageRate,
            @NotNull BigDecimal remaining,
            OffsetDateTime expectedCriterionAt,
            BigDecimal averageReplacementInterval,
            @NotNull @Valid PartUsageStatus status) {}

    public enum PartLifeBasis {
        OPERATING_HOURS,
        ELAPSED_DAYS
    }

    public record PartUsagePageResponse(
            @NotNull Boolean success,
            @NotNull @Valid List<PartUsageItem> data,
            @NotNull @Valid PageMeta meta) {}

    public record PartUsageItem(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long partId,
            @NotNull String partName,
            String specification,
            String managementNumber,
            @NotNull @Valid PartLifeBasis lifeBasis,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal usage,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal replacementCriterion,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal usageRate,
            @NotNull BigDecimal remaining,
            OffsetDateTime expectedCriterionAt,
            BigDecimal averageReplacementInterval,
            @NotNull @Valid PartUsageStatus status,
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long equipmentId,
            @NotNull String equipmentName,
            @NotNull String lineName,
            @NotNull String processName) {}

    public record PartReplacementHistoryResponse(
            @NotNull Boolean success, @NotNull @Valid PartReplacementHistoryData data) {}

    public record PartReplacementHistoryData(
            BigDecimal averageReplacementInterval,
            @NotNull @Valid List<PartReplacementRecord> items) {}

    public record PartReplacementRecord(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long replacementId,
            @NotNull OffsetDateTime replacedAt,
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long partId,
            @NotNull String partName,
            @NotNull String reason,
            @NotNull String workContent,
            @NotNull @Valid UserBrief worker) {}

    public record PartReplacementResponse(
            @NotNull Boolean success, @NotNull @Valid PartReplacementRecord data) {}

    public record PartReplacementRequest(
            @NotNull OffsetDateTime replacedAt,
            @NotNull @NotBlank @Size(min = 1, max = 1000) String reason,
            @NotNull @NotBlank @Size(min = 1, max = 4000) String workContent) {}

    public record AlarmResponseStatisticsResponse(
            @NotNull Boolean success, @NotNull @Valid AlarmResponseStatistics data) {}

    public record AlarmResponseStatistics(
            @NotNull @DecimalMin(value = "0", inclusive = true)
                    BigDecimal averageAcknowledgementMinutes,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal averageRepairMinutes,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer delayedAlarmCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) @DecimalMax("100")
                    BigDecimal delayedAlarmRate) {}

    public record FalsePositiveStatisticsResponse(
            @NotNull Boolean success, @NotNull @Valid FalsePositiveStatistics data) {}

    public record FalsePositiveStatistics(
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer count,
            @NotNull @DecimalMin(value = "0", inclusive = true) @DecimalMax("100") BigDecimal rate,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal responseMinutes,
            @NotNull @Valid List<NamedCount> equipmentRanking,
            @NotNull @Valid List<NamedRate> dataItemRates) {}

    public record NamedCount(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long id,
            @NotNull String name,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer count) {}

    public record NamedRate(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long id,
            @NotNull String name,
            @NotNull @DecimalMin(value = "0", inclusive = true) @DecimalMax("100")
                    BigDecimal rate) {}

    public record DailyAlarmStatisticsResponse(
            @NotNull Boolean success, @NotNull @Valid List<DailyAlarmCount> data) {}

    public record DailyAlarmCount(
            @NotNull LocalDate date,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer criticalCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer cautionCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer falsePositiveCount) {}

    public record AvailabilityStatisticsResponse(
            @NotNull Boolean success, @NotNull @Valid List<AvailabilityMetric> data) {}

    public record AvailabilityMetric(
            @NotNull AvailabilityMetricTargetType targetType,
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long targetId,
            @NotNull String targetName,
            @NotNull @DecimalMin(value = "0", inclusive = true) @DecimalMax("100")
                    BigDecimal availabilityRate,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal operatingMinutes,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal plannedMinutes) {}

    public enum AvailabilityMetricTargetType {
        LINE,
        EQUIPMENT
    }

    public record DowntimeStatisticsResponse(
            @NotNull Boolean success, @NotNull @Valid List<DowntimeMetric> data) {}

    public record DowntimeMetric(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long equipmentId,
            @NotNull String equipmentName,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal faultMinutes,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal idleMinutes,
            @NotNull @DecimalMin(value = "0", inclusive = true)
                    BigDecimal communicationLostMinutes) {}

    public record EquipmentRankingResponse(
            @NotNull Boolean success, @NotNull @Valid List<EquipmentRankingItem> data) {}

    public record EquipmentRankingItem(
            @NotNull @DecimalMin(value = "1", inclusive = true) Integer rank,
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long equipmentId,
            @NotNull String equipmentName,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer faultCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal downtimeMinutes,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal averageRepairMinutes,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal lossAmount) {}

    public record LossStatisticsResponse(
            @NotNull Boolean success, @NotNull @Valid LossStatistics data) {}

    public record LossStatistics(
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal todayLossAmount,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal weeklyLossAmount,
            @NotNull @Valid List<DailyLoss> daily) {}

    public record DailyLoss(
            @NotNull LocalDate date,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal lossAmount,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal assemblyStopMinutes,
            Long causeEquipmentId,
            String causeEquipmentName) {}

    public record LossAvoidanceResponse(
            @NotNull Boolean success, @NotNull @Valid LossAvoidance data) {}

    public record LossAvoidance(
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal todayAvoidedAmount,
            @NotNull BigDecimal previousDayDifference,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal monthlyAvoidedAmount,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer todayFaultCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer recoveredBeforeStopCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) @DecimalMax("100")
                    BigDecimal recoveryRate) {}

    public record TelegramLinkResponse(
            @NotNull Boolean success, @NotNull @Valid TelegramLink data) {}

    public record TelegramLink(
            @NotNull String linkUrl,
            @NotNull String qrPayload,
            @NotNull OffsetDateTime expiresAt) {}

    public record TelegramConnectionResponse(
            @NotNull Boolean success, @NotNull @Valid TelegramConnection data) {}

    public record TelegramConnection(@NotNull Boolean connected, OffsetDateTime linkedAt) {}

    public record TelegramTestMessageResponse(
            @NotNull Boolean success, @NotNull @Valid TelegramTestMessageResult data) {}

    public record TelegramTestMessageResult(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long userId,
            @NotNull @Valid DeliveryResult result,
            String failureReason,
            @NotNull OffsetDateTime sentAt) {}

    public enum DeliveryResult {
        SUCCESS,
        FAILURE
    }

    public record TelegramTestMessageRequest(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long userId) {}

    public record TelegramDeliveryPageResponse(
            @NotNull Boolean success,
            @NotNull @Valid TelegramDeliveryPageData data,
            @NotNull @Valid PageMeta meta) {}

    public record TelegramDeliveryPageData(
            @NotNull @Valid TelegramDeliverySummary summary,
            @NotNull @Valid List<TelegramDelivery> items,
            @NotNull @Valid List<TelegramRecipientCount> recipientCounts) {}

    public record TelegramDeliverySummary(
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer totalCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer successCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer failureCount) {}

    public record TelegramDelivery(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long deliveryId,
            @NotNull OffsetDateTime sentAt,
            @Valid UserBrief recipient,
            Long alarmId,
            @NotNull String messageSummary,
            @NotNull @Valid DeliveryResult result,
            String failureReason,
            String recipientName) {}

    public record TelegramRecipientCount(
            @NotNull @Valid UserBrief recipient,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer totalCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer successCount,
            @NotNull @DecimalMin(value = "0", inclusive = true) Integer failureCount) {}

    public record DutyScheduleListResponse(
            @NotNull Boolean success, @NotNull @Valid List<DutySchedule> data) {}

    public record DutySchedule(
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long scheduleId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @Valid UserBrief dutyUser,
            @NotNull @Valid DutyScheduleStatus status) {}

    public enum DutyScheduleStatus {
        CURRENT,
        UPCOMING
    }

    public record DutyScheduleResponse(
            @NotNull Boolean success, @NotNull @Valid DutySchedule data) {}

    public record DutyScheduleWriteRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @DecimalMin(value = "1", inclusive = true) @Valid Long dutyUserId) {}

    public record DutyCalendarResponse(
            @NotNull Boolean success, @NotNull @Valid List<DutyCalendarDay> data) {}

    public record DutyCalendarDay(
            @NotNull LocalDate date, @NotNull @Valid List<DutySchedule> schedules) {}

    public enum GetEquipmentRankingsSortBy {
        faultCount,
        downtimeMinutes,
        averageRepairMinutes,
        lossAmount
    }
}
