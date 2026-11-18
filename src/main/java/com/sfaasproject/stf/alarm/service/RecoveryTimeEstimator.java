package com.sfaasproject.stf.alarm.service;

import org.springframework.stereotype.Service;

import java.math.*;
import java.time.*;
import java.util.List;

/** v7 final decision: same equipment/item, CLOSED CRITICAL, closed before the current fault. */
@Service
public class RecoveryTimeEstimator {
    public record ClosedAlarm(
            long equipmentId,
            long dataItemId,
            AlarmGrade grade,
            OffsetDateTime occurredAt,
            OffsetDateTime closedAt) {}

    public record Estimate(
            int repairHistoryCount,
            BigDecimal averageRepairMinutes,
            OffsetDateTime recoveryExpectedAt) {}

    public Estimate estimate(
            long equipment, long item, OffsetDateTime fault, List<ClosedAlarm> history) {
        if (equipment <= 0 || item <= 0 || fault == null || history == null)
            throw new IllegalArgumentException("Invalid recovery inputs");
        var selected =
                history.stream()
                        .filter(
                                a ->
                                        a != null
                                                && a.equipmentId() == equipment
                                                && a.dataItemId() == item
                                                && a.grade() == AlarmGrade.CRITICAL
                                                && a.occurredAt() != null
                                                && a.closedAt() != null
                                                && !a.closedAt().isBefore(a.occurredAt())
                                                && a.closedAt().isBefore(fault))
                        .toList();
        if (selected.isEmpty()) return new Estimate(0, null, null);
        BigDecimal nanos =
                selected.stream()
                        .map(
                                a ->
                                        BigDecimal.valueOf(
                                                Duration.between(a.occurredAt(), a.closedAt())
                                                        .toNanos()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(selected.size()), 0, RoundingMode.HALF_UP);
        return new Estimate(
                selected.size(),
                nanos.divide(BigDecimal.valueOf(60_000_000_000L), 12, RoundingMode.HALF_UP),
                fault.plusNanos(nanos.longValueExact()));
    }
}
