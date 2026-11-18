package com.sfaasproject.stf.equipment.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** EQP-002~005 조회 결과. 시각은 DB 와 같은 한국 시간이다. 쓰지 않는 칸은 null 이다. */
public final class EquipmentQueryView {

    private EquipmentQueryView() {
    }

    /**
     * openapi ProcessSummary.
     *
     * @param representativeStatus 공정 설비 중 가장 나쁜 상태(COMMUNICATION_LOST → FAULT → CAUTION → IDLE → RUNNING).
     *        설비가 없으면 null. 2026-11-18 추가(프론트 요청)
     */
    public record Process(long processId, String processName, int processOrder, boolean hasAbnormalEquipment,
            String representativeStatus) { }

    /** openapi CurrentDataItem. */
    public record CurrentItem(long dataItemId, String dataItemName, String valueType, BigDecimal value,
            String displayValue, String unit, String valueStatus, BigDecimal cautionThreshold,
            BigDecimal criticalThreshold, LocalDateTime measuredAt) { }

    /**
     * openapi AbnormalMeasurement. threshold 는 v7 그대로 벗어난 쪽 기준이고, 나머지 메타정보는 2026-11-18 추가했다.
     */
    public record Abnormal(long dataItemId, String dataItemName, BigDecimal currentValue, String displayValue,
            String unit, String valueStatus, BigDecimal threshold, BigDecimal cautionThreshold,
            BigDecimal criticalThreshold, String thresholdDirection, int decimalPlaces) { }

    /** openapi EquipmentSummary. */
    public record Summary(long equipmentId, String equipmentCode, String equipmentName, long lineId, String lineName,
            String lineType, long processId, String processName, int processOrder, String status,
            LocalDateTime statusStartedAt, int activeAlarmCount, List<CurrentItem> mainMeasurements,
            List<Abnormal> abnormalMeasurements) { }

    /** openapi EquipmentDetailData. basicInfo 는 기준정보 설비 응답과 같은 값을 쓴다. */
    public record Detail(MasterEquipmentView basicInfo, String status, LocalDateTime statusStartedAt,
            LocalDateTime lastReceivedAt, List<CurrentItem> measurements, List<CurrentItem> stateValues,
            List<CurrentItem> cumulativeValues) { }
}
