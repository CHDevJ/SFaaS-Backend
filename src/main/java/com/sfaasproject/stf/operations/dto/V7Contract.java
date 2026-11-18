package com.sfaasproject.stf.operations.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

/** DTOs from docs/openapi.yaml 7.0.0. No persistence entities or removed v5/v6 fields. */
public final class V7Contract {
    private V7Contract() {}

    public record EquipmentLayoutResponse(
            @NotNull Boolean success, @NotNull @Valid EquipmentLayoutData data) {}

    public record AssemblyStopForecastResponse(
            @NotNull Boolean success, @NotNull @Valid AssemblyStopForecastData data) {}

    public record InventoryResponse(
            @NotNull Boolean success, @NotNull @Valid List<InventoryLine> data) {}

    public record InventoryHistoryResponse(
            @NotNull Boolean success, @NotNull @Valid List<InventoryHistoryPoint> data) {}

    public record InventoryDetailResponse(
            @NotNull Boolean success, @NotNull @Valid InventoryDetail data) {}

    public record LineListResponse(
            @NotNull Boolean success, @NotNull @Valid List<LineSummary> data) {}

    public record ProcessListResponse(
            @NotNull Boolean success, @NotNull @Valid List<ProcessSummary> data) {}

    public record ProcessEquipmentResponse(
            @NotNull Boolean success, @NotNull @Valid List<EquipmentSummary> data) {}

    public record EquipmentDetailResponse(
            @NotNull Boolean success, @NotNull @Valid EquipmentDetailData data) {}

    public record DataItemListResponse(
            @NotNull Boolean success, @NotNull @Valid List<CurrentDataItem> data) {}

    public enum AlarmGrade {
        CAUTION,
        CRITICAL
    }

    public enum AlarmProcessStatus {
        ACTIVE,
        CLOSED
    }

    public record AlarmPageResponse(
            @NotNull Boolean success,
            @NotNull @Valid AlarmPageData data,
            @NotNull @Valid PageMeta meta) {}

    public record AlarmDetailResponse(
            @NotNull Boolean success, @NotNull @Valid AlarmDetailData data) {}

    public record AlarmActionRequest(
            @NotNull @NotBlank @Size(max = 200) String equipmentText,
            @Size(max = 1000) String cause,
            @NotNull @NotBlank @Size(max = 4000) String actionContent) {}

    public record AlarmActionResponse(
            @NotNull Boolean success, @NotNull @Valid AlarmActionData data) {}

    public record AlarmHistoryPageResponse(
            @NotNull Boolean success,
            @NotNull @Valid List<AlarmHistoryItem> data,
            @NotNull @Valid PageMeta meta) {}

    public record FaultHistoryResponse(
            @NotNull Boolean success, @NotNull @Valid FaultHistoryData data) {}

    public record AlarmResponseStatisticsResponse(
            @NotNull Boolean success, @NotNull @Valid AlarmResponseStatistics data) {}

    public record DailyAlarmStatisticsResponse(
            @NotNull Boolean success, @NotNull @Valid List<DailyAlarmCount> data) {}

    public record AvailabilityStatisticsResponse(
            @NotNull Boolean success, @NotNull @Valid List<AvailabilityMetric> data) {}

    public record DowntimeStatisticsResponse(
            @NotNull Boolean success, @NotNull @Valid List<DowntimeMetric> data) {}

    public record EquipmentRankingResponse(
            @NotNull Boolean success, @NotNull @Valid List<EquipmentRankingItem> data) {}

    public record LossStatisticsResponse(
            @NotNull Boolean success, @NotNull @Valid LossStatistics data) {}

    public record JudgementSettingsResponse(
            @NotNull Boolean success, @NotNull @Valid JudgementSettingsData data) {}

    public record JudgementSettings(
            @NotNull @DecimalMin("10") @DecimalMax("300") Integer communicationLossSeconds,
            @NotNull @DecimalMin("1") @DecimalMax("10") Integer alarmConsecutiveCount,
            @NotNull @DecimalMin("5") @DecimalMax("120") Integer inventoryWarningMinutes,
            @NotNull @DecimalMin("1") @DecimalMax("60") Integer inventoryDangerMinutes) {}

    public record CalculationSettingsResponse(
            @NotNull Boolean success, @NotNull @Valid CalculationSettings data) {}

    public record CalculationSettingsUpdateRequest(
            @NotNull @DecimalMin("0") @DecimalMax("100000000") BigDecimal unitPrice,
            @NotNull @DecimalMin("5") @DecimalMax("60") Integer speedWindowMinutes,
            @NotNull @DecimalMin("1") @DecimalMax("1000") Integer hourlyProduction) {}

    public record TelegramLinkResponse(
            @NotNull Boolean success, @NotNull @Valid TelegramLink data) {}

    public record TelegramConnectionResponse(
            @NotNull Boolean success, @NotNull @Valid TelegramConnection data) {}

    public record TelegramTestMessageResponse(
            @NotNull Boolean success, @NotNull @Valid TelegramTestMessageResult data) {}

    public record TelegramTestMessageRequest(@NotNull Long userId) {}

    public enum DeliveryResult {
        SUCCESS,
        FAILURE
    }

    public record TelegramDeliveryPageResponse(
            @NotNull Boolean success,
            @NotNull @Valid TelegramDeliveryPageData data,
            @NotNull @Valid PageMeta meta) {}

    public record AlarmHourlyCount(
            @NotNull OffsetDateTime from,
            @NotNull OffsetDateTime to,
            @NotNull @DecimalMin("0") Integer cautionCount,
            @NotNull @DecimalMin("0") Integer criticalCount) {}

    public record RecentAlarmListResponse(
            @NotNull OffsetDateTime windowFrom,
            @NotNull OffsetDateTime windowTo,
            @NotNull @Valid List<AlarmHourlyCount> hourlyCounts,
            @NotNull Boolean success,
            @NotNull @Valid List<AlarmSummary> data,
            @NotNull @Valid PageMeta meta) {}

    public record CriticalEquipmentResponse(
            @NotNull Boolean success, @NotNull @Valid List<CriticalEquipmentCount> data) {}

    public record StopCauseResponse(
            @NotNull Boolean success, @NotNull @Valid List<StopCause> data) {}

    public record TelegramUpdateResult(
            @NotNull Boolean success, @NotNull @Valid TelegramUpdateResultData data) {}

    public record TelegramUpdate(
            @NotNull Long update_id, @NotNull @Valid TelegramUpdateMessage message) {}

    public record InventoryStageThresholds(
            @NotNull @DecimalMin("5") @DecimalMax("120") Integer inventoryWarningMinutes,
            @NotNull @DecimalMin("1") @DecimalMax("60") Integer inventoryDangerMinutes) {}

    public enum ForecastStatus {
        READY,
        NO_DEPLETION,
        NO_RECOVERY_HISTORY,
        INSUFFICIENT_DATA
    }

    public record UserBrief(
            @NotNull Long userId,
            @NotNull @Size(max = 100) String name,
            @NotNull @Valid Permission permission) {}

    public record EquipmentEvent(
            @NotNull String eventId,
            @NotNull Long sequence,
            @NotNull Long equipmentId,
            @NotNull @Valid EquipmentStatus status,
            @NotNull OffsetDateTime statusStartedAt,
            @NotNull OffsetDateTime occurredAt) {}

    public record MeasurementEvent(
            @NotNull String eventId,
            @NotNull Long sequence,
            @NotNull Long equipmentId,
            @NotNull Long dataItemId,
            @NotNull @Valid DataValueType valueType,
            @NotNull BigDecimal value,
            @NotNull OffsetDateTime measuredAt) {}

    public record AlarmEvent(
            @NotNull @Size(max = 200) String eventId,
            @NotNull @DecimalMin("0") Long sequence,
            @NotNull Long alarmId,
            @NotNull @Valid AlarmEventEventType eventType,
            @NotNull @Valid AlarmGrade grade,
            @NotNull @Valid AlarmProcessStatus processStatus,
            @NotNull OffsetDateTime occurredAt) {}

    public record AlarmCountEvent(
            @NotNull @Size(max = 200) String eventId,
            @NotNull @DecimalMin("0") Long sequence,
            @NotNull @DecimalMin("0") Integer activeCount) {}

    public record InventoryEvent(
            @NotNull String eventId,
            @NotNull Long sequence,
            @NotNull Long lineId,
            @DecimalMin("0") Long quantity,
            @Valid InventoryEventStage stage,
            BigDecimal remainingMinutes,
            @NotNull OffsetDateTime occurredAt,
            @NotNull @Valid InventoryStageThresholds stageThresholds) {}

    public record PersonalEvent(
            @NotNull String eventId,
            @NotNull @DecimalMin("0") Long sequence,
            @NotNull @Valid PersonalEventType type,
            @NotNull Long alarmId,
            @NotNull OffsetDateTime occurredAt) {}

    public record EquipmentLayoutData(
            @NotNull @Valid List<EquipmentStatusCount> statusCounts,
            @NotNull @Valid List<EquipmentSummary> equipment,
            @Valid List<EquipmentLayoutDataProcessGroupsItem> processGroups,
            @DecimalMin("1") Long suggestedEquipmentId) {}

    public record AssemblyStopForecastData(
            Long representativeLineId,
            OffsetDateTime earliestAssemblyStopExpectedAt,
            @NotNull @Valid List<LineStopForecast> lines,
            @NotNull OffsetDateTime calculatedAt) {}

    public record InventoryLine(
            @NotNull Long lineId,
            @NotNull String lineName,
            @DecimalMin("0") Long quantity,
            @DecimalMin("0") BigDecimal consumptionPerMinute,
            @DecimalMin("0") BigDecimal productionPerMinute,
            @NotNull OffsetDateTime calculatedAt,
            @NotNull @Valid InventoryLineCalculationStatus calculationStatus,
            @NotNull @Valid LineType lineType,
            @Valid InventoryLineStage stage,
            @DecimalMin("0") BigDecimal remainingMinutes,
            OffsetDateTime assemblyStopExpectedAt,
            @NotNull @Valid InventoryStageThresholds stageThresholds,
            @NotNull @Valid InventoryLineInventorySource inventorySource) {}

    public record InventoryHistoryPoint(
            @NotNull OffsetDateTime recordedAt,
            @NotNull Long lineId,
            String lineName,
            @NotNull @DecimalMin("0") Long quantity) {}

    public record InventoryDetail(
            @NotNull Long lineId,
            @NotNull String lineName,
            @DecimalMin("0") Long quantity,
            @DecimalMin("0") BigDecimal consumptionPerMinute,
            @DecimalMin("0") BigDecimal productionPerMinute,
            @NotNull OffsetDateTime calculatedAt,
            @NotNull @Valid InventoryDetailCalculationStatus calculationStatus,
            @NotNull @Valid LineType lineType,
            @Valid InventoryDetailStage stage,
            @DecimalMin("0") BigDecimal remainingMinutes,
            OffsetDateTime assemblyStopExpectedAt,
            @NotNull @Valid InventoryStageThresholds stageThresholds,
            @NotNull @Valid InventoryDetailInventorySource inventorySource) {}

    public record LineSummary(
            @NotNull Long lineId,
            @NotNull @Size(max = 100) String lineName,
            @NotNull @DecimalMin("1") Integer displayOrder,
            @NotNull @Valid LineType lineType) {}

    public record ProcessSummary(
            @NotNull Long processId,
            @NotNull @Size(max = 100) String processName,
            @NotNull @DecimalMin("1") Integer processOrder,
            @NotNull Boolean hasAbnormalEquipment) {}

    public record EquipmentSummary(
            @NotNull Long equipmentId,
            @NotNull @Size(max = 100) String equipmentCode,
            @NotNull @Size(max = 200) String equipmentName,
            @NotNull Long lineId,
            @NotNull String lineName,
            @NotNull Long processId,
            @NotNull String processName,
            @NotNull @DecimalMin("1") Integer processOrder,
            @NotNull @Valid EquipmentStatus status,
            @NotNull OffsetDateTime statusStartedAt,
            @NotNull @DecimalMin("0") Integer activeAlarmCount,
            @Valid List<CurrentDataItem> mainMeasurements,
            @Valid List<AbnormalMeasurement> abnormalMeasurements,
            @NotNull @Valid LineType lineType) {}

    public record EquipmentDetailData(
            @NotNull @Valid EquipmentBasicInfo basicInfo,
            @NotNull @Valid EquipmentStatus status,
            @NotNull OffsetDateTime statusStartedAt,
            @NotNull OffsetDateTime lastReceivedAt,
            Boolean delayed,
            @NotNull @Valid List<CurrentDataItem> measurements,
            @NotNull @Valid List<CurrentDataItem> stateValues,
            @NotNull @Valid List<CurrentDataItem> cumulativeValues) {}

    public record CurrentDataItem(
            @NotNull Long dataItemId,
            @NotNull @Size(max = 100) String dataItemName,
            @NotNull @Valid DataValueType valueType,
            @NotNull Object value,
            String displayValue,
            @Size(max = 30) String unit,
            @Valid CurrentDataItemValueStatus valueStatus,
            BigDecimal cautionThreshold,
            BigDecimal criticalThreshold,
            @NotNull OffsetDateTime measuredAt) {}

    public record AlarmPageData(
            @NotNull @DecimalMin("0") Integer yesterdayOccurredCount,
            @NotNull @DecimalMin("0") Integer activeCautionCount,
            @NotNull @DecimalMin("0") Integer activeCriticalCount,
            @NotNull @DecimalMin("0") Integer activeCount,
            @NotNull @DecimalMin("0") Integer todayOccurredCount,
            @NotNull @DecimalMin("0") Integer todayClosedCount,
            @DecimalMin("0") BigDecimal averageActionMinutes,
            @NotNull @Valid List<AlarmSummary> items) {}

    public record PageMeta(
            @NotNull @DecimalMin("0") Integer page,
            @NotNull @DecimalMin("1") Integer size,
            @NotNull @DecimalMin("0") Long totalElements,
            @NotNull @DecimalMin("0") Integer totalPages) {}

    public record AlarmDetailData(
            @NotNull Long alarmId,
            @NotNull Long equipmentId,
            @Size(max = 100) String equipmentCode,
            @NotNull @Size(max = 200) String equipmentName,
            Long lineId,
            @Size(max = 100) String lineName,
            @NotNull Long dataItemId,
            @Size(max = 100) String dataItemName,
            @NotNull @Size(max = 1000) String alarmMessage,
            @Size(max = 1000) String cause,
            BigDecimal currentValue,
            BigDecimal occurredValue,
            BigDecimal thresholdValue,
            String unit,
            @NotNull @Valid AlarmGrade grade,
            @NotNull @Valid AlarmValueStatus valueStatus,
            @NotNull @Valid AlarmProcessStatus processStatus,
            @NotNull OffsetDateTime occurredAt,
            OffsetDateTime recoveredAt,
            OffsetDateTime closedAt,
            @NotNull @DecimalMin("0") Integer elapsedSeconds,
            @DecimalMin("0") Integer durationSeconds,
            String actionContent,
            @Valid List<MeasurementPoint> sparkline,
            BigDecimal cautionThreshold,
            BigDecimal criticalThreshold,
            @Valid AlarmDetailDataThresholdDirection thresholdDirection,
            @DecimalMin("0") @DecimalMax("10") Integer decimalPlaces,
            OffsetDateTime assemblyStopExpectedAt,
            @NotNull @Valid List<MeasurementPoint> trendBeforeAndAfter,
            @NotNull @Valid List<AlarmSummary> equipmentHistories,
            @Valid AlarmDetailDataAction action) {}

    public record AlarmActionData(
            @NotNull Long alarmId,
            @NotNull @Valid AlarmActionDataProcessStatus processStatus,
            @NotNull @Size(max = 200) String equipmentText,
            @NotNull @Size(max = 1000) String cause,
            @NotNull @Size(max = 4000) String actionContent,
            @NotNull OffsetDateTime closedAt,
            @NotNull @Valid UserBrief handledBy,
            @NotNull @DecimalMin("0") Integer durationSeconds) {}

    public record AlarmHistoryItem(
            @NotNull Long alarmId,
            @NotNull Long equipmentId,
            @Size(max = 100) String equipmentCode,
            @NotNull @Size(max = 200) String equipmentName,
            Long lineId,
            @Size(max = 100) String lineName,
            @NotNull Long dataItemId,
            @Size(max = 100) String dataItemName,
            @NotNull @Size(max = 1000) String alarmMessage,
            @Size(max = 1000) String cause,
            BigDecimal currentValue,
            BigDecimal occurredValue,
            BigDecimal thresholdValue,
            String unit,
            @NotNull @Valid AlarmGrade grade,
            @NotNull @Valid AlarmValueStatus valueStatus,
            @NotNull @Valid AlarmProcessStatus processStatus,
            @NotNull OffsetDateTime occurredAt,
            OffsetDateTime recoveredAt,
            OffsetDateTime closedAt,
            @NotNull @DecimalMin("0") Integer elapsedSeconds,
            @DecimalMin("0") Integer durationSeconds,
            String actionContent,
            @Valid List<MeasurementPoint> sparkline,
            BigDecimal cautionThreshold,
            BigDecimal criticalThreshold,
            @Valid AlarmHistoryItemThresholdDirection thresholdDirection,
            @DecimalMin("0") @DecimalMax("10") Integer decimalPlaces) {}

    public record FaultHistoryData(
            @NotNull @DecimalMin("0") Integer monthlyFaultCount,
            @DecimalMin("0") BigDecimal averageRepairMinutes,
            @DecimalMin("0") BigDecimal averageFailureIntervalMinutes,
            @NotNull @Valid List<FaultHistoryItem> items) {}

    public record AlarmResponseStatistics(
            @NotNull @DecimalMin("0") Integer activeCautionCount,
            @NotNull @DecimalMin("0") Integer activeCriticalCount,
            Long longestActionEquipmentId,
            String longestActionEquipmentName,
            @NotNull @DecimalMin("0") Integer totalCount,
            @NotNull @DecimalMin("0") Integer cautionCount,
            @NotNull @DecimalMin("0") Integer criticalCount,
            @NotNull @DecimalMin("0") Integer activeCount,
            @DecimalMin("0") BigDecimal averageActionMinutes,
            @DecimalMin("0") BigDecimal longestActionMinutes) {}

    public record DailyAlarmCount(
            @NotNull LocalDate date,
            @NotNull @DecimalMin("0") Integer criticalCount,
            @NotNull @DecimalMin("0") Integer cautionCount) {}

    public record AvailabilityMetric(
            @NotNull @Valid AvailabilityMetricTargetType targetType,
            @NotNull Long targetId,
            @NotNull String targetName,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal availabilityRate,
            @NotNull @DecimalMin("0") BigDecimal operatingMinutes,
            @NotNull @DecimalMin("0") BigDecimal plannedMinutes) {}

    public record DowntimeMetric(
            @NotNull Long equipmentId,
            @NotNull String equipmentName,
            @NotNull @DecimalMin("0") BigDecimal faultMinutes,
            @NotNull @DecimalMin("0") BigDecimal idleMinutes,
            @NotNull @DecimalMin("0") BigDecimal communicationLostMinutes) {}

    public record EquipmentRankingItem(
            @NotNull @DecimalMin("1") Integer rank,
            @NotNull Long equipmentId,
            @NotNull String equipmentName,
            @NotNull @DecimalMin("0") Integer faultCount,
            @NotNull @DecimalMin("0") BigDecimal downtimeMinutes,
            @DecimalMin("0") BigDecimal averageRepairMinutes,
            @NotNull @DecimalMin("0") BigDecimal lossAmount) {}

    public record LossStatistics(
            @NotNull @DecimalMin("0") BigDecimal todayLossAmount,
            @NotNull @DecimalMin("0") BigDecimal weeklyLossAmount,
            @NotNull @Valid List<DailyLoss> daily) {}

    public record JudgementSettingsData(
            @NotNull @DecimalMin("10") @DecimalMax("300") Integer communicationLossSeconds,
            @NotNull @DecimalMin("1") @DecimalMax("10") Integer alarmConsecutiveCount,
            @NotNull @DecimalMin("5") @DecimalMax("120") Integer inventoryWarningMinutes,
            @NotNull @DecimalMin("1") @DecimalMax("60") Integer inventoryDangerMinutes,
            @NotNull @Valid List<JudgementSettingRange> ranges) {}

    public record CalculationSettings(
            @NotNull @DecimalMin("0") @DecimalMax("100000000") BigDecimal unitPrice,
            @NotNull @DecimalMin("5") @DecimalMax("60") Integer speedWindowMinutes,
            @NotNull @DecimalMin("1") @DecimalMax("1000") Integer hourlyProduction,
            @Valid List<JudgementSettingRange> ranges) {}

    public record TelegramLink(
            @NotNull String linkUrl,
            @NotNull String qrPayload,
            @NotNull OffsetDateTime expiresAt) {}

    public record TelegramConnection(@NotNull Boolean connected, OffsetDateTime linkedAt) {}

    public record TelegramTestMessageResult(
            @NotNull String message,
            @NotNull Long userId,
            @NotNull @Valid DeliveryResult result,
            String failureReason,
            @NotNull OffsetDateTime sentAt) {}

    public record TelegramDeliveryPageData(
            @NotNull @Valid TelegramDeliverySummary summary,
            @NotNull @Valid List<TelegramDelivery> items,
            @NotNull @Valid List<TelegramRecipientCount> recipientCounts) {}

    public record AlarmSummary(
            @NotNull Long alarmId,
            @NotNull Long equipmentId,
            @Size(max = 100) String equipmentCode,
            @NotNull @Size(max = 200) String equipmentName,
            Long lineId,
            @Size(max = 100) String lineName,
            @NotNull Long dataItemId,
            @Size(max = 100) String dataItemName,
            @NotNull @Size(max = 1000) String alarmMessage,
            @Size(max = 1000) String cause,
            BigDecimal currentValue,
            BigDecimal occurredValue,
            BigDecimal thresholdValue,
            String unit,
            @NotNull @Valid AlarmGrade grade,
            @NotNull @Valid AlarmValueStatus valueStatus,
            @NotNull @Valid AlarmProcessStatus processStatus,
            @NotNull OffsetDateTime occurredAt,
            OffsetDateTime recoveredAt,
            OffsetDateTime closedAt,
            @NotNull @DecimalMin("0") Integer elapsedSeconds,
            @DecimalMin("0") Integer durationSeconds,
            String actionContent,
            @Valid List<MeasurementPoint> sparkline,
            BigDecimal cautionThreshold,
            BigDecimal criticalThreshold,
            @Valid AlarmSummaryThresholdDirection thresholdDirection,
            @DecimalMin("0") @DecimalMax("10") Integer decimalPlaces) {}

    public record CriticalEquipmentCount(
            @NotNull Long equipmentId,
            @NotNull @Size(max = 200) String equipmentName,
            @NotNull @DecimalMin("0") Integer criticalCount) {}

    public record StopCause(
            @NotNull @Size(max = 1000) String cause,
            @NotNull @Valid StopCauseSource source,
            @NotNull @DecimalMin("0") BigDecimal downtimeMinutes) {}

    public record TelegramUpdateResultData(@NotNull Boolean processed) {}

    public record TelegramUpdateMessage(
            @NotNull @Size(max = 500) String text,
            @NotNull @Valid TelegramUpdateMessageChat chat) {}

    public enum Permission {
        ADMIN,
        USER
    }

    public enum EquipmentStatus {
        RUNNING,
        IDLE,
        CAUTION,
        FAULT,
        COMMUNICATION_LOST
    }

    public enum DataValueType {
        MEASUREMENT,
        STATE,
        CUMULATIVE
    }

    public enum AlarmEventEventType {
        CREATED,
        GRADE_ESCALATED,
        VALUE_RECOVERED,
        CLOSED
    }

    public enum InventoryEventStage {
        MAINTAIN,
        CAUTION,
        DANGER
    }

    public enum PersonalEventType {
        CREATED,
        GRADE_ESCALATED,
        ALARM_CLOSED
    }

    public record EquipmentStatusCount(
            @NotNull @Valid EquipmentStatus status, @NotNull @DecimalMin("0") Integer count) {}

    public record EquipmentLayoutDataProcessGroupsItem(
            @NotNull Long processId,
            @NotNull Long lineId,
            @NotNull @DecimalMin("1") Integer processOrder,
            @NotNull @DecimalMin("0") Integer equipmentCount,
            @NotNull @DecimalMin("0") Integer abnormalEquipmentCount,
            @NotNull @Valid List<Long> equipmentIds) {}

    public record LineStopForecast(
            @NotNull Long lineId,
            @NotNull String lineName,
            @DecimalMin("0") Long currentInventory,
            BigDecimal netConsumptionPerMinute,
            @DecimalMin("0") BigDecimal remainingMinutes,
            @Valid LineStopForecastStage stage,
            OffsetDateTime assemblyStopExpectedAt,
            Long sourceEquipmentId,
            Long activeAlarmId,
            OffsetDateTime recoveryExpectedAt,
            BigDecimal marginMinutes,
            @Valid LineStopForecastMarginStatus marginStatus,
            @DecimalMin("0") BigDecimal hourlyProduction,
            @DecimalMin("0") BigDecimal unitPrice,
            @DecimalMin("0") BigDecimal expectedLossAmount,
            @NotNull @Valid LineType lineType,
            @NotNull @DecimalMin("0") Integer repairHistoryCount,
            @DecimalMin("0") BigDecimal averageRepairMinutes,
            @DecimalMin("0") BigDecimal shortageMinutes,
            @NotNull Boolean unitPriceConfigured,
            @NotNull @Valid ForecastStatus forecastStatus,
            @NotNull @Valid InventoryStageThresholds stageThresholds) {}

    public enum InventoryLineCalculationStatus {
        READY,
        INSUFFICIENT_DATA,
        COUNTER_RESET_PENDING,
        COUNTER_INCONSISTENT
    }

    public enum LineType {
        PART,
        ASSEMBLY
    }

    public enum InventoryLineStage {
        MAINTAIN,
        CAUTION,
        DANGER
    }

    public enum InventoryLineInventorySource {
        INVENTORY_SYSTEM,
        DEMO_PRODUCTION
    }

    public enum InventoryDetailCalculationStatus {
        READY,
        INSUFFICIENT_DATA,
        COUNTER_RESET_PENDING,
        COUNTER_INCONSISTENT
    }

    public enum InventoryDetailStage {
        MAINTAIN,
        CAUTION,
        DANGER
    }

    public enum InventoryDetailInventorySource {
        INVENTORY_SYSTEM,
        DEMO_PRODUCTION
    }

    public record AbnormalMeasurement(
            @NotNull Long dataItemId,
            @NotNull String dataItemName,
            @NotNull BigDecimal currentValue,
            String unit,
            @NotNull @Valid AbnormalMeasurementValueStatus valueStatus,
            BigDecimal threshold) {}

    public record EquipmentBasicInfo(
            @NotNull Long equipmentId,
            @NotNull String equipmentCode,
            @NotNull String equipmentName,
            @NotNull String equipmentType,
            String serialNumber,
            @NotNull Long lineId,
            @NotNull String lineName,
            @NotNull Long processId,
            @NotNull String processName,
            @NotNull Integer processOrder,
            @NotNull String communicationMethod,
            String photoUrl,
            OffsetDateTime registeredAt) {}

    public enum CurrentDataItemValueStatus {
        NORMAL,
        CAUTION,
        CRITICAL
    }

    public enum AlarmValueStatus {
        ABNORMAL,
        RECOVERED
    }

    public record MeasurementPoint(@NotNull OffsetDateTime measuredAt, BigDecimal value) {}

    public enum AlarmDetailDataThresholdDirection {
        ABOVE,
        BELOW
    }

    public record AlarmDetailDataAction(
            @NotNull Long alarmId,
            @NotNull @Valid AlarmDetailDataActionProcessStatus processStatus,
            @NotNull @Size(max = 200) String equipmentText,
            @NotNull @Size(max = 1000) String cause,
            @NotNull @Size(max = 4000) String actionContent,
            @NotNull OffsetDateTime closedAt,
            @NotNull @Valid UserBrief handledBy,
            @NotNull @DecimalMin("0") Integer durationSeconds) {}

    public enum AlarmActionDataProcessStatus {
        CLOSED
    }

    public enum AlarmHistoryItemThresholdDirection {
        ABOVE,
        BELOW
    }

    public record FaultHistoryItem(
            @NotNull Long alarmId,
            @NotNull OffsetDateTime occurredAt,
            @NotNull OffsetDateTime closedAt,
            @NotNull @Size(max = 1000) String cause,
            @NotNull @Size(max = 4000) String actionContent,
            @NotNull @DecimalMin("0") Integer durationSeconds,
            @NotNull @Valid UserBrief handledBy) {}

    public enum AvailabilityMetricTargetType {
        LINE,
        EQUIPMENT
    }

    public record DailyLoss(
            @NotNull LocalDate date,
            @NotNull @DecimalMin("0") BigDecimal lossAmount,
            @NotNull @DecimalMin("0") BigDecimal assemblyStopMinutes,
            Long causeEquipmentId,
            String causeEquipmentName) {}

    public record JudgementSettingRange(
            @NotNull String field, @NotNull BigDecimal minimum, @NotNull BigDecimal maximum) {}

    public record TelegramDeliverySummary(
            @NotNull @DecimalMin("0") Integer totalCount,
            @NotNull @DecimalMin("0") Integer successCount,
            @NotNull @DecimalMin("0") Integer failureCount) {}

    public record TelegramDelivery(
            @NotNull Long deliveryId,
            @NotNull OffsetDateTime sentAt,
            @Valid TelegramDeliveryRecipient recipient,
            String recipientName,
            Long alarmId,
            @NotNull String messageSummary,
            @NotNull @Valid DeliveryResult result,
            String failureReason) {}

    public record TelegramRecipientCount(
            @NotNull @Valid UserBrief recipient,
            @NotNull @DecimalMin("0") Integer totalCount,
            @NotNull @DecimalMin("0") Integer successCount,
            @NotNull @DecimalMin("0") Integer failureCount) {}

    public enum AlarmSummaryThresholdDirection {
        ABOVE,
        BELOW
    }

    public enum StopCauseSource {
        ALARM,
        COMMUNICATION_LOST
    }

    public record TelegramUpdateMessageChat(@NotNull Long id) {}

    public enum LineStopForecastStage {
        MAINTAIN,
        CAUTION,
        DANGER
    }

    public enum LineStopForecastMarginStatus {
        AVAILABLE,
        SHORTAGE
    }

    public enum AbnormalMeasurementValueStatus {
        CAUTION,
        CRITICAL
    }

    public enum AlarmDetailDataActionProcessStatus {
        CLOSED
    }

    public record TelegramDeliveryRecipient(
            @NotNull Long userId,
            @NotNull @Size(max = 100) String name,
            @NotNull @Valid Permission permission) {}
}
