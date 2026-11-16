package com.sfaasproject.stf.statistics.service;

import com.sfaasproject.stf.equipment.service.EquipmentStatusDecisionService.Status;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** Clipped status-history durations for availability and downtime statistics. */
@Service
public class EquipmentTimeStatisticsService {
    public record Interval(Status status, LocalDateTime startedAt, LocalDateTime endedAt) { }
    public record Durations(BigDecimal runningMinutes, BigDecimal idleMinutes,
            BigDecimal cautionMinutes, BigDecimal faultMinutes,
            BigDecimal communicationLostMinutes) { }
    public record Availability(BigDecimal operatingMinutes, BigDecimal plannedMinutes,
            BigDecimal ratePercent) { }

    public Durations durations(List<Interval> intervals, LocalDateTime from, LocalDateTime to) {
        if (intervals == null || from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("Invalid status-history period");
        }
        long[] seconds = new long[Status.values().length];
        for (Interval interval : intervals) {
            if (interval.status() == null || interval.startedAt() == null) {
                throw new IllegalArgumentException("Invalid status interval");
            }
            LocalDateTime end = interval.endedAt() == null ? to : interval.endedAt();
            if (end.isBefore(interval.startedAt())) throw new IllegalArgumentException("Invalid status interval");
            LocalDateTime start = interval.startedAt().isBefore(from) ? from : interval.startedAt();
            if (end.isAfter(to)) end = to;
            if (end.isAfter(start)) {
                seconds[interval.status().ordinal()] = Math.addExact(
                        seconds[interval.status().ordinal()], Duration.between(start, end).toSeconds());
            }
        }
        return new Durations(minutes(seconds[Status.RUNNING.ordinal()]),
                minutes(seconds[Status.IDLE.ordinal()]),
                minutes(seconds[Status.CAUTION.ordinal()]),
                minutes(seconds[Status.FAULT.ordinal()]),
                minutes(seconds[Status.COMMUNICATION_LOST.ordinal()]));
    }

    public Availability availability(Durations durations, BigDecimal plannedMinutes) {
        if (durations == null || plannedMinutes == null || plannedMinutes.signum() <= 0) {
            throw new IllegalArgumentException("Positive planned minutes are required");
        }
        BigDecimal operating = durations.runningMinutes();
        BigDecimal rate = operating.multiply(BigDecimal.valueOf(100))
                .divide(plannedMinutes, 2, RoundingMode.HALF_UP);
        if (rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Operating time exceeds planned time");
        }
        return new Availability(operating, plannedMinutes, rate);
    }

    private BigDecimal minutes(long seconds) {
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 2,
                RoundingMode.HALF_UP);
    }
}
