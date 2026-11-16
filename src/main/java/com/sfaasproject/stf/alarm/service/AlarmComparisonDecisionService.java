package com.sfaasproject.stf.alarm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** MEAS-002: one alarm, ten minutes before and after occurrence. */
@Service
public class AlarmComparisonDecisionService {
    public record Point(LocalDateTime measuredAt, BigDecimal value) { }
    public record Comparison(List<Point> before, List<Point> after,
            BigDecimal beforeAverage, BigDecimal afterAverage,
            BigDecimal changeRatePercent) { }

    public Comparison compare(LocalDateTime occurredAt, List<Point> points) {
        if (occurredAt == null || points == null) {
            throw new IllegalArgumentException("Occurrence time and points are required");
        }
        List<Point> before = points.stream()
                .filter(p -> !p.measuredAt().isBefore(occurredAt.minusMinutes(10))
                        && p.measuredAt().isBefore(occurredAt))
                .sorted(Comparator.comparing(Point::measuredAt)).toList();
        List<Point> after = points.stream()
                .filter(p -> !p.measuredAt().isBefore(occurredAt)
                        && !p.measuredAt().isAfter(occurredAt.plusMinutes(10)))
                .sorted(Comparator.comparing(Point::measuredAt)).toList();
        BigDecimal beforeAverage = average(before);
        BigDecimal afterAverage = average(after);
        BigDecimal change = beforeAverage == null || afterAverage == null
                || beforeAverage.signum() == 0 ? null
                : afterAverage.subtract(beforeAverage).multiply(BigDecimal.valueOf(100))
                        .divide(beforeAverage.abs(), 2, RoundingMode.HALF_UP);
        return new Comparison(before, after, beforeAverage, afterAverage, change);
    }

    private BigDecimal average(List<Point> points) {
        if (points.isEmpty()) return null;
        BigDecimal total = points.stream().map(Point::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(points.size()), 4, RoundingMode.HALF_UP);
    }
}
