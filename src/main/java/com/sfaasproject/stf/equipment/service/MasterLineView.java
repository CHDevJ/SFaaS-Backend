package com.sfaasproject.stf.equipment.service;

import java.time.LocalDateTime;
import java.util.List;

/** 라인 기준정보 한 건과 공정 순서대로 놓인 설비 (openapi v7 MasterLine). */
public record MasterLineView(long lineId, String lineName, int displayOrder, String lineType,
        List<EquipmentSummaryView> equipment) {

    /** openapi EquipmentSummary 의 필수 항목. 시각은 DB 와 같은 한국 시간이다. */
    public record EquipmentSummaryView(
            long equipmentId,
            String equipmentCode,
            String equipmentName,
            long lineId,
            String lineName,
            long processId,
            String processName,
            int processOrder,
            String status,
            LocalDateTime statusStartedAt,
            int activeAlarmCount,
            String lineType) { }
}
