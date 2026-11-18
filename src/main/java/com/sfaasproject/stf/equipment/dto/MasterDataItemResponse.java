package com.sfaasproject.stf.equipment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.equipment.service.MasterDataItemView;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 데이터 항목 한 건 (openapi MasterDataItem). latestValue 는 nullable 이 아니라서 받은 값이 없으면 칸을 뺀다.
 */
public record MasterDataItemResponse(
        long dataItemId,
        String dataItemCode,
        String dataItemName,
        String valueType,
        String unit,
        int collectionIntervalSeconds,
        Integer decimalPlaces,
        String zeroDisplayName,
        String oneDisplayName,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double latestValue,
        OffsetDateTime lastReceivedAt,
        Threshold measurementThreshold,
        StateAlarm stateAlarmCondition,
        Statistics statistics) {

    /** openapi MeasurementThreshold. */
    public record Threshold(double cautionValue, double criticalValue, String direction) {
        static Threshold from(MasterDataItemView.Threshold view) {
            return view == null ? null : new Threshold(view.cautionValue(), view.criticalValue(), view.direction());
        }
    }

    /** openapi StateAlarmCondition. */
    public record StateAlarm(int alarmValue, String grade) {
        static StateAlarm from(MasterDataItemView.StateAlarm view) {
            return view == null ? null : new StateAlarm(view.alarmValue(), view.grade());
        }
    }

    /** openapi ThresholdStatistics. */
    public record Statistics(Double recentMinimum, Double recentAverage, Double recentMaximum,
            int alarmCountLast30Days) { }

    /** MST-013 의 data (openapi ThresholdData). */
    public record AppliedThreshold(long dataItemId, Threshold measurementThreshold, StateAlarm stateAlarmCondition,
            OffsetDateTime appliedAt) {

        public static AppliedThreshold from(MasterDataItemView.AppliedThreshold view) {
            return new AppliedThreshold(view.dataItemId(), Threshold.from(view.measurementThreshold()),
                    StateAlarm.from(view.stateAlarmCondition()), offset(view.appliedAt()));
        }
    }

    public static MasterDataItemResponse from(MasterDataItemView view) {
        MasterDataItemView.Statistics stats = view.statistics();
        return new MasterDataItemResponse(view.dataItemId(), view.dataItemCode(), view.dataItemName(),
                view.valueType(), view.unit(), view.collectionIntervalSeconds(), view.decimalPlaces(),
                view.zeroDisplayName(), view.oneDisplayName(), view.latestValue(), offset(view.lastReceivedAt()),
                Threshold.from(view.measurementThreshold()), StateAlarm.from(view.stateAlarmCondition()),
                new Statistics(stats.recentMinimum(), stats.recentAverage(), stats.recentMaximum(),
                        stats.alarmCountLast30Days()));
    }

    private static OffsetDateTime offset(LocalDateTime local) {
        return local == null ? null : KstTime.toOffset(KstTime.toInstant(local));
    }
}
