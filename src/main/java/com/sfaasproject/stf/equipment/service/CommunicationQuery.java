package com.sfaasproject.stf.equipment.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * COM-001/002 통신 현황 조회 포트. 설비 상태(equipment_state)와 상태 이력(equipment_status_history)은
 * 수집 쪽에서 쓰는 테이블이라 JDBC 로만 읽는다. 시각은 DB 와 같은 한국 시간이다.
 */
public interface CommunicationQuery {

    /**
     * 사용 중인 라인의 사용 중인 설비 한 대.
     *
     * @param status 지금 상태. 상태 줄이 없으면 null(값을 받은 적 없음)
     */
    record Row(long equipmentId, String equipmentCode, String equipmentName, String lineName,
            String communicationMethod, String status, LocalDateTime statusSince, LocalDateTime lastReceivedAt,
            LocalDateTime createdAt) { }

    record Interval(LocalDateTime startedAt, LocalDateTime endedAt) { }

    /** lineId·processId 는 각각 null 이면 조건을 걸지 않는다. 둘 다 null 이면 전체. */
    List<Row> equipment(Long lineId, Long processId);

    /** 설비별로 from~to 와 겹치는 COMMUNICATION_LOST 상태 이력. endedAt 이 null 이면 아직 끊겨 있다. */
    Map<Long, List<Interval>> disconnections(Collection<Long> equipmentIds, LocalDateTime from, LocalDateTime to);
}
