package com.sfaasproject.stf.equipment.service;

import java.time.LocalDateTime;

/**
 * 기준정보 설비 한 건 (openapi v7 MasterEquipment). 시각은 DB 와 같은 한국 시간이다.
 *
 * @param photoUrl 10분 동안 열리는 MST-018 서명 주소. 사진이 없으면 null
 * @param lastReceivedAt 마지막으로 값을 받은 시각. 받은 적이 없으면 null
 */
public record MasterEquipmentView(
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
        LocalDateTime registeredAt,
        LocalDateTime lastReceivedAt) { }
