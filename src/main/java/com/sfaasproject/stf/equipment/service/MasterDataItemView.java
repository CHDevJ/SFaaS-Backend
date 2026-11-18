package com.sfaasproject.stf.equipment.service;

import java.time.LocalDateTime;

/**
 * 데이터 항목 한 건 (openapi MasterDataItem). 시각은 DB 와 같은 한국 시간이다. 쓰지 않는 칸은 null 이다.
 *
 * @param latestValue 마지막으로 받은 값. 받은 적이 없으면 null
 * @param statistics 최근 7일 측정값 범위(측정값 항목만)와 최근 30일 알람 수
 */
public record MasterDataItemView(
        long dataItemId,
        String dataItemCode,
        String dataItemName,
        String valueType,
        String unit,
        int collectionIntervalSeconds,
        Integer decimalPlaces,
        String zeroDisplayName,
        String oneDisplayName,
        Double latestValue,
        LocalDateTime lastReceivedAt,
        Threshold measurementThreshold,
        StateAlarm stateAlarmCondition,
        Statistics statistics) {

    public record Threshold(double cautionValue, double criticalValue, String direction) { }

    public record StateAlarm(int alarmValue, String grade) { }

    public record Statistics(Double recentMinimum, Double recentAverage, Double recentMaximum,
            int alarmCountLast30Days) { }

    /** MST-013 결과 (openapi ThresholdData). */
    public record AppliedThreshold(long dataItemId, Threshold measurementThreshold, StateAlarm stateAlarmCondition,
            LocalDateTime appliedAt) { }
}
