package com.sfaasproject.stf.measurement.dto;

import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.measurement.service.MeasurementTrendQuery;
import com.sfaasproject.stf.measurement.service.MeasurementTrendService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * MEAS-001 응답 data (openapi MultiMeasurementData). 각 series 의 disconnections·currentlyConnected 는 설비 기준이라
 * 위쪽 값과 같다. thresholdDirection·decimalPlaces·zero/oneDisplayName 은 2026-11-18 추가한 추세 메타정보다.
 */
public record MeasurementTrendResponse(long equipmentId, List<Series> series, List<Gap> disconnections,
        boolean currentlyConnected) {

    public record Point(OffsetDateTime measuredAt, Double value) { }

    public record Marker(long alarmId, OffsetDateTime occurredAt, double value, String grade) { }

    public record Gap(OffsetDateTime startedAt, OffsetDateTime endedAt) { }

    public record Abnormal(OffsetDateTime startedAt, OffsetDateTime endedAt, String grade) { }

    public record Series(long equipmentId, long dataItemId, String dataItemName, String valueType, String unit,
            Double cautionThreshold, Double criticalThreshold, String thresholdDirection, int decimalPlaces,
            String zeroDisplayName, String oneDisplayName, List<Point> points, List<Marker> alarmMarkers,
            List<Gap> disconnections, boolean currentlyConnected, List<Abnormal> abnormalIntervals,
            int bucketSeconds) { }

    public static MeasurementTrendResponse from(MeasurementTrendService.Trend trend) {
        List<Gap> gaps = trend.disconnections().stream().map(MeasurementTrendResponse::gap).toList();
        List<Series> series = trend.series().stream().map(s -> new Series(trend.equipmentId(), s.dataItemId(),
                s.dataItemName(), s.valueType(), s.unit(), s.cautionThreshold(), s.criticalThreshold(),
                s.thresholdDirection(), s.decimalPlaces(), s.zeroDisplayName(), s.oneDisplayName(),
                s.points().stream().map(p -> new Point(offset(p.at()), p.value())).toList(),
                s.alarmMarkers().stream().map(m -> new Marker(m.alarmId(), offset(m.occurredAt()), m.value(),
                        m.grade())).toList(),
                gaps, trend.currentlyConnected(),
                s.abnormalIntervals().stream().map(a -> new Abnormal(offset(a.startedAt()), offset(a.endedAt()),
                        a.grade())).toList(),
                s.bucketSeconds())).toList();
        return new MeasurementTrendResponse(trend.equipmentId(), series, gaps, trend.currentlyConnected());
    }

    private static Gap gap(MeasurementTrendQuery.Interval interval) {
        return new Gap(offset(interval.startedAt()), offset(interval.endedAt()));
    }

    private static OffsetDateTime offset(LocalDateTime local) {
        return local == null ? null : KstTime.toOffset(KstTime.toInstant(local));
    }
}
