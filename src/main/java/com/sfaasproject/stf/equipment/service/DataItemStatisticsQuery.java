package com.sfaasproject.stf.equipment.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * MST-010 응답의 최근 값과 통계 참고값을 읽는 조회 포트. 측정값과 알람은 수집·알람 영역의 테이블이므로
 * 엔티티나 리포지토리를 참조하지 않고 이 포트로만 읽는다. 시각은 DB 와 같은 한국 시간이다.
 */
public interface DataItemStatisticsQuery {

    /** measurement_latest 의 마지막 값과 측정·수신 시각. 받은 적이 없는 항목은 결과에 없다. */
    record Latest(double value, LocalDateTime measuredAt, LocalDateTime receivedAt) { }

    /** from 이후 측정값의 최솟값·평균·최댓값. 값이 없으면 결과에 없다. */
    record Range(double minimum, double average, double maximum) { }

    Map<Long, Latest> latest(Collection<Long> dataItemIds);

    Map<Long, Range> ranges(Collection<Long> dataItemIds, LocalDateTime from);

    /** from 이후 발생한 알람 수. 알람이 없는 항목은 결과에 없다. */
    Map<Long, Integer> alarmCounts(Collection<Long> dataItemIds, LocalDateTime from);
}
