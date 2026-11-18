package com.sfaasproject.stf.equipment.dto;

import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.equipment.service.EquipmentQueryView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/** EQP-002~005 응답 DTO (openapi ProcessSummary, EquipmentSummary, CurrentDataItem, EquipmentDetailData). */
public final class EquipmentQueryResponse {

    private EquipmentQueryResponse() {
    }

    /** openapi ProcessSummary (+ representativeStatus). */
    public record Process(long processId, String processName, int processOrder, boolean hasAbnormalEquipment,
            String representativeStatus) {

        public static Process from(EquipmentQueryView.Process view) {
            return new Process(view.processId(), view.processName(), view.processOrder(),
                    view.hasAbnormalEquipment(), view.representativeStatus());
        }
    }

    /** openapi CurrentDataItem. */
    public record CurrentItem(long dataItemId, String dataItemName, String valueType, BigDecimal value,
            String displayValue, String unit, String valueStatus, BigDecimal cautionThreshold,
            BigDecimal criticalThreshold, OffsetDateTime measuredAt) {

        public static CurrentItem from(EquipmentQueryView.CurrentItem view) {
            return new CurrentItem(view.dataItemId(), view.dataItemName(), view.valueType(), view.value(),
                    view.displayValue(), view.unit(), view.valueStatus(), view.cautionThreshold(),
                    view.criticalThreshold(), offset(view.measuredAt()));
        }
    }

    /** openapi AbnormalMeasurement (+ displayValue, 양쪽 기준, 방향, 소수 자릿수). */
    public record Abnormal(long dataItemId, String dataItemName, BigDecimal currentValue, String displayValue,
            String unit, String valueStatus, BigDecimal threshold, BigDecimal cautionThreshold,
            BigDecimal criticalThreshold, String thresholdDirection, int decimalPlaces) {

        static Abnormal from(EquipmentQueryView.Abnormal view) {
            return new Abnormal(view.dataItemId(), view.dataItemName(), view.currentValue(), view.displayValue(),
                    view.unit(), view.valueStatus(), view.threshold(), view.cautionThreshold(),
                    view.criticalThreshold(), view.thresholdDirection(), view.decimalPlaces());
        }
    }

    /** openapi EquipmentSummary. */
    public record Summary(long equipmentId, String equipmentCode, String equipmentName, long lineId, String lineName,
            String lineType, long processId, String processName, int processOrder, String status,
            OffsetDateTime statusStartedAt, int activeAlarmCount, List<CurrentItem> mainMeasurements,
            List<Abnormal> abnormalMeasurements) {

        public static Summary from(EquipmentQueryView.Summary view) {
            return new Summary(view.equipmentId(), view.equipmentCode(), view.equipmentName(), view.lineId(),
                    view.lineName(), view.lineType(), view.processId(), view.processName(), view.processOrder(),
                    view.status(), offset(view.statusStartedAt()), view.activeAlarmCount(),
                    view.mainMeasurements().stream().map(CurrentItem::from).toList(),
                    view.abnormalMeasurements().stream().map(Abnormal::from).toList());
        }
    }

    /** openapi EquipmentBasicInfo. */
    public record BasicInfo(long equipmentId, String equipmentCode, String equipmentName, String equipmentType,
            String serialNumber, long lineId, String lineName, long processId, String processName, int processOrder,
            String communicationMethod, String photoUrl, OffsetDateTime registeredAt) { }

    /** openapi EquipmentDetailData. */
    public record Detail(BasicInfo basicInfo, String status, OffsetDateTime statusStartedAt,
            OffsetDateTime lastReceivedAt, List<CurrentItem> measurements, List<CurrentItem> stateValues,
            List<CurrentItem> cumulativeValues) {

        public static Detail from(EquipmentQueryView.Detail view) {
            var b = view.basicInfo();
            return new Detail(new BasicInfo(b.equipmentId(), b.equipmentCode(), b.equipmentName(), b.equipmentType(),
                    b.serialNumber(), b.lineId(), b.lineName(), b.processId(), b.processName(), b.processOrder(),
                    b.communicationMethod(), b.photoUrl(), offset(b.registeredAt())),
                    view.status(), offset(view.statusStartedAt()), offset(view.lastReceivedAt()),
                    view.measurements().stream().map(CurrentItem::from).toList(),
                    view.stateValues().stream().map(CurrentItem::from).toList(),
                    view.cumulativeValues().stream().map(CurrentItem::from).toList());
        }
    }

    private static OffsetDateTime offset(LocalDateTime local) {
        return local == null ? null : KstTime.toOffset(KstTime.toInstant(local));
    }
}
