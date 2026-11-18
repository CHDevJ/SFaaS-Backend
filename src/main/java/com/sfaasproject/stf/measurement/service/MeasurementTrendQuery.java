package com.sfaasproject.stf.measurement.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * MEAS-001 추세 조회 포트. 측정 이력·설비 상태 이력·알람은 수집·알람 영역 테이블이라 JDBC 로만 읽는다.
 * 시각은 DB 와 같은 한국 시간이다. 구간은 from 이상 to 이하다.
 */
public interface MeasurementTrendQuery {

    record Point(LocalDateTime at, Double value) { }

    record Interval(LocalDateTime startedAt, LocalDateTime endedAt) { }

    record Alarm(long alarmId, long dataItemId, LocalDateTime occurredAt, LocalDateTime closedAt, double value,
            String grade) { }

    /** 항목별 구간 안의 측정값 건수. */
    Map<Long, Integer> counts(Collection<Long> dataItemIds, LocalDateTime from, LocalDateTime to);

    /** 항목별 원본 측정값, 시각순. */
    Map<Long, List<Point>> raw(Collection<Long> dataItemIds, LocalDateTime from, LocalDateTime to);

    /**
     * 항목별 집계값, 구간 시작순. anchor 부터 bucketSeconds 단위로 나눈 칸의 시작 시각을 돌려준다.
     *
     * @param averaged true 면 평균(측정값), false 면 칸의 마지막 값(상태값·누적값)
     */
    Map<Long, List<Point>> buckets(Collection<Long> dataItemIds, LocalDateTime from, LocalDateTime to,
            LocalDateTime anchor, int bucketSeconds, boolean averaged);

    /** 구간과 겹치는 통신 끊김(COMMUNICATION_LOST) 상태 이력. endedAt 이 null 이면 아직 끊겨 있다. */
    List<Interval> disconnections(long equipmentId, LocalDateTime from, LocalDateTime to);

    /** 지금 상태 줄이 있고 COMMUNICATION_LOST 가 아니면 true. */
    boolean currentlyConnected(long equipmentId);

    /** 구간과 겹치는 항목들의 알람(발생 시각이 to 이하이고, 종료되지 않았거나 from 이후에 종료). */
    List<Alarm> alarms(Collection<Long> dataItemIds, LocalDateTime from, LocalDateTime to);
}
