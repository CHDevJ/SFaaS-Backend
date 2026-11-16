package com.sfaasproject.stf.part.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** Part-life usage since installation or the latest replacement. */
@Service
public class PartLifeDecisionService {
    public enum Basis { OPERATING_HOURS, ELAPSED_DAYS }
    public enum Status { NORMAL, DUE_SOON, OVERDUE }
    public record RunningInterval(LocalDateTime startedAt, LocalDateTime endedAt) { }
    public record Usage(Basis basis, BigDecimal used, BigDecimal criterion,
            BigDecimal usageRate, BigDecimal remaining,
            LocalDateTime expectedCriterionAt, Status status) { }

    public Usage calculate(Basis basis, LocalDateTime lastReplacedAt, int criterion,
            List<RunningInterval> runningIntervals, LocalDateTime now,
            BigDecimal dueSoonPercent) {
        return calculate(basis, lastReplacedAt, BigDecimal.valueOf(criterion),
                runningIntervals, now, dueSoonPercent);
    }

    public Usage calculate(Basis basis, LocalDateTime lastReplacedAt, BigDecimal criterion,
            List<RunningInterval> runningIntervals, LocalDateTime now,
            BigDecimal dueSoonPercent) {
        if (basis == null || lastReplacedAt == null || now == null || now.isBefore(lastReplacedAt)
                || criterion == null || criterion.signum() <= 0 || runningIntervals == null || dueSoonPercent == null
                || dueSoonPercent.signum() < 0 || dueSoonPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Invalid part-life inputs");
        }
        long seconds = basis == Basis.ELAPSED_DAYS
                ? Duration.between(lastReplacedAt, now).getSeconds()
                : operatingSeconds(lastReplacedAt, now, runningIntervals);
        BigDecimal divisor = basis == Basis.ELAPSED_DAYS
                ? BigDecimal.valueOf(86400) : BigDecimal.valueOf(3600);
        BigDecimal used = BigDecimal.valueOf(seconds).divide(divisor, 4, RoundingMode.HALF_UP);
        BigDecimal limit = criterion;
        BigDecimal rate = used.multiply(BigDecimal.valueOf(100))
                .divide(limit, 2, RoundingMode.HALF_UP);
        Status status = used.compareTo(limit) >= 0 ? Status.OVERDUE
                : rate.compareTo(dueSoonPercent) >= 0 ? Status.DUE_SOON : Status.NORMAL;
        LocalDateTime expected = basis == Basis.ELAPSED_DAYS
                ? lastReplacedAt.plusNanos(criterion.multiply(BigDecimal.valueOf(86_400_000_000_000L))
                        .setScale(0, RoundingMode.HALF_UP).longValueExact()) : null;
        return new Usage(basis, used, limit, rate, limit.subtract(used), expected, status);
    }

    private long operatingSeconds(LocalDateTime since, LocalDateTime now,
            List<RunningInterval> intervals) {
        long total = 0;
        for (RunningInterval interval : intervals) {
            if (interval.startedAt() == null) throw new IllegalArgumentException("Run start is required");
            LocalDateTime end = interval.endedAt() == null ? now : interval.endedAt();
            if (end.isBefore(interval.startedAt())) throw new IllegalArgumentException("Invalid run interval");
            LocalDateTime start = interval.startedAt().isBefore(since) ? since : interval.startedAt();
            if (end.isAfter(now)) end = now;
            if (end.isAfter(start)) total = Math.addExact(total, Duration.between(start, end).getSeconds());
        }
        return total;
    }
}
