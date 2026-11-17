package com.sfaasproject.stf.statistics.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;

/** Money is rounded once after summing exact duration * output * price numerators. */
@Service
public class ActualLossCalculator {
    private static final BigDecimal NANOS_PER_HOUR = BigDecimal.valueOf(3_600_000_000_000L);

    public record Interval(
            OffsetDateTime from,
            OffsetDateTime to,
            BigDecimal hourlyOutput,
            BigDecimal unitPrice) {}

    public BigDecimal calculate(Collection<Interval> intervals) {
        if (intervals == null) throw new IllegalArgumentException("Intervals are required");
        BigDecimal numerator = BigDecimal.ZERO;
        for (var interval : intervals) {
            if (interval == null
                    || interval.from() == null
                    || interval.to() == null
                    || interval.to().isBefore(interval.from())
                    || interval.hourlyOutput() == null
                    || interval.hourlyOutput().signum() < 0
                    || interval.unitPrice() == null
                    || interval.unitPrice().signum() < 0)
                throw new IllegalArgumentException(
                        "A valid loss interval and snapshot are required");
            var duration = Duration.between(interval.from(), interval.to());
            var nanos =
                    BigDecimal.valueOf(duration.getSeconds())
                            .movePointRight(9)
                            .add(BigDecimal.valueOf(duration.getNano()));
            numerator =
                    numerator.add(
                            nanos.multiply(interval.hourlyOutput()).multiply(interval.unitPrice()));
        }
        return numerator.divide(NANOS_PER_HOUR, 0, RoundingMode.HALF_UP);
    }
}
