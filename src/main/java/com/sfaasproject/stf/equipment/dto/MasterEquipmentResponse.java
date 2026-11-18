package com.sfaasproject.stf.equipment.dto;

import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.equipment.service.MasterEquipmentService.EquipmentPage;
import com.sfaasproject.stf.equipment.service.MasterEquipmentView;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/** 기준정보 설비 한 건 (openapi v7 MasterEquipment). */
public record MasterEquipmentResponse(
        long equipmentId,
        String equipmentCode,
        String equipmentName,
        String equipmentType,
        String serialNumber,
        long lineId,
        String lineName,
        long processId,
        String processName,
        int processOrder,
        String communicationMethod,
        String photoUrl,
        OffsetDateTime registeredAt,
        OffsetDateTime lastReceivedAt) {

    /** MST-001 의 data (openapi MasterEquipmentPageData). */
    public record PageData(long registeredCount, long monitoredCount, List<MasterEquipmentResponse> items) {

        public static PageData from(EquipmentPage page) {
            return new PageData(page.registeredCount(), page.monitoredCount(),
                    page.items().getContent().stream().map(MasterEquipmentResponse::from).toList());
        }
    }

    public static MasterEquipmentResponse from(MasterEquipmentView view) {
        return new MasterEquipmentResponse(view.equipmentId(), view.equipmentCode(), view.equipmentName(),
                view.equipmentType(), view.serialNumber(), view.lineId(), view.lineName(), view.processId(),
                view.processName(), view.processOrder(), view.communicationMethod(), view.photoUrl(),
                offset(view.registeredAt()), offset(view.lastReceivedAt()));
    }

    private static OffsetDateTime offset(LocalDateTime local) {
        return KstTime.toOffset(KstTime.toInstant(local));
    }
}
