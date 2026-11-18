package com.sfaasproject.stf.equipment.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * 설비의 현재 상태, 마지막 수신 시각, 발생 중인 알람 수를 읽는 조회 포트. 기준정보 응답에 쓴다.
 * 알람은 B 영역 데이터이므로 엔티티나 리포지토리를 참조하지 않고 이 포트로만 읽는다.
 */
public interface EquipmentRuntimeQuery {

    /**
     * @param status 현재 상태(EquipmentStatus 값). 상태 줄이 없으면 null
     * @param statusSince 상태 시작 시각(한국 시간). 상태 줄이 없으면 null
     * @param lastReceivedAt 마지막으로 값을 받은 시각(한국 시간). 받은 적이 없으면 null
     * @param activeAlarmCount 종료되지 않은 알람 수
     */
    record Runtime(String status, LocalDateTime statusSince, LocalDateTime lastReceivedAt, int activeAlarmCount) { }

    /** 요청한 설비의 현재 상태. 상태 줄도 알람도 없는 설비는 결과에 없을 수 있다. */
    Map<Long, Runtime> find(Collection<Long> equipmentIds);

    /** 종료되지 않은 알람이 있는지. 설비 삭제(MST-004) 거부 판단에 쓴다. */
    default boolean hasActiveAlarm(long equipmentId) {
        Runtime runtime = find(java.util.List.of(equipmentId)).get(equipmentId);
        return runtime != null && runtime.activeAlarmCount() > 0;
    }
}
