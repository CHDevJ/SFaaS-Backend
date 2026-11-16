package com.sfaasproject.stf.measurement.service;

import com.sfaasproject.stf.common.DomainException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Builds the data portion of MEAS-001 from repository-supplied rows. */
@Service
public class MeasurementTrendDecisionService {
    public record Item(long id, long equipmentId, String name, String valueType, String unit,
            BigDecimal cautionThreshold, BigDecimal criticalThreshold) { }
    public record Point(LocalDateTime measuredAt, BigDecimal value) { }
    public record AlarmMarker(long alarmId, LocalDateTime occurredAt, BigDecimal value, String grade) { }
    public record Disconnection(LocalDateTime startedAt, LocalDateTime endedAt) { }
    public record Series(long equipmentId, long dataItemId, String dataItemName,
            String valueType, String unit, BigDecimal cautionThreshold,
            BigDecimal criticalThreshold, List<Point> points, List<AlarmMarker> alarmMarkers,
            List<Disconnection> disconnections, boolean currentlyConnected) { }

    public Series assemble(long equipmentId, Item item, LocalDateTime from, LocalDateTime to,
            List<Point> measurements, List<AlarmMarker> alarms,
            List<Disconnection> statusIntervals, boolean currentlyConnected) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "조회 기간을 확인해 주세요.");
        }
        if (item == null || item.equipmentId() != equipmentId) {
            throw new DomainException(HttpStatus.NOT_FOUND, "DATA_ITEM_NOT_FOUND",
                    "데이터 항목을 찾을 수 없습니다.");
        }
        Objects.requireNonNull(measurements);
        Objects.requireNonNull(alarms);
        Objects.requireNonNull(statusIntervals);
        List<Point> points = measurements.stream()
                .filter(p -> inRange(p.measuredAt(), from, to))
                .sorted(Comparator.comparing(Point::measuredAt)).toList();
        List<AlarmMarker> markers = alarms.stream()
                .filter(a -> inRange(a.occurredAt(), from, to))
                .sorted(Comparator.comparing(AlarmMarker::occurredAt)
                        .thenComparingLong(AlarmMarker::alarmId)).toList();
        List<Disconnection> gaps = clipAndMerge(statusIntervals, from, to);
        return new Series(equipmentId, item.id(), item.name(), item.valueType(), item.unit(),
                item.cautionThreshold(), item.criticalThreshold(), points, markers, gaps,
                currentlyConnected);
    }

    private boolean inRange(LocalDateTime at, LocalDateTime from, LocalDateTime to) {
        return at != null && !at.isBefore(from) && !at.isAfter(to);
    }

    private List<Disconnection> clipAndMerge(List<Disconnection> intervals,
            LocalDateTime from, LocalDateTime to) {
        List<Disconnection> clipped = intervals.stream()
                .filter(i -> i.startedAt() != null && !i.startedAt().isAfter(to)
                        && (i.endedAt() == null || !i.endedAt().isBefore(from)))
                .map(i -> new Disconnection(i.startedAt().isBefore(from) ? from : i.startedAt(),
                        i.endedAt() == null || i.endedAt().isAfter(to) ? to : i.endedAt()))
                .sorted(Comparator.comparing(Disconnection::startedAt)).toList();
        var merged = new ArrayList<Disconnection>();
        for (Disconnection gap : clipped) {
            if (merged.isEmpty()) {
                merged.add(gap);
                continue;
            }
            int last = merged.size() - 1;
            Disconnection preceding = merged.get(last);
            if (!gap.startedAt().isAfter(preceding.endedAt())) {
                LocalDateTime end = gap.endedAt().isAfter(preceding.endedAt())
                        ? gap.endedAt() : preceding.endedAt();
                merged.set(last, new Disconnection(preceding.startedAt(), end));
            } else {
                merged.add(gap);
            }
        }
        return List.copyOf(merged);
    }
}
