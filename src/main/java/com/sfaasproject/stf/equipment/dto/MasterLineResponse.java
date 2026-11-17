package com.sfaasproject.stf.equipment.dto;

import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.equipment.service.MasterLineView;
import com.sfaasproject.stf.equipment.service.MasterLineView.EquipmentSummaryView;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 라인 기준정보 한 건 (openapi MasterLine = LineSummary + equipment).
 * 설비는 EquipmentSummary 의 필수 항목만 담는다. 주요 측정값·이상 값·교체 임박 부품은 선택 항목이라 넣지 않는다.
 */
public record MasterLineResponse(long lineId, String lineName, int displayOrder, List<Equipment> equipment) {

    public record Equipment(
            long equipmentId,
            String equipmentCode,
            String equipmentName,
            long lineId,
            String lineName,
            long processId,
            String processName,
            int processOrder,
            String status,
            OffsetDateTime statusStartedAt,
            int activeAlarmCount) {

        static Equipment from(EquipmentSummaryView view) {
            return new Equipment(view.equipmentId(), view.equipmentCode(), view.equipmentName(), view.lineId(),
                    view.lineName(), view.processId(), view.processName(), view.processOrder(), view.status(),
                    KstTime.toOffset(KstTime.toInstant(view.statusStartedAt())), view.activeAlarmCount());
        }
    }

    public static MasterLineResponse from(MasterLineView view) {
        return new MasterLineResponse(view.lineId(), view.lineName(), view.displayOrder(),
                view.equipment().stream().map(Equipment::from).toList());
    }
}
