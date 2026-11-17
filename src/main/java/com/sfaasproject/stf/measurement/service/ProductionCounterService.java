package com.sfaasproject.stf.measurement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.stereotype.Service;

/** Delta of a cumulative production counter; first observation establishes the baseline. */
@Service
public class ProductionCounterService {
    public int quantity(BigDecimal previous, LocalDateTime previousAt, BigDecimal current,
            LocalDateTime currentAt, LocalTime resetTime) {
        if (current == null || current.signum() < 0 || current.stripTrailingZeros().scale() > 0
                || currentAt == null) {
            throw new IllegalArgumentException("Production counter must be a nonnegative integer with a timestamp");
        }
        if (previous == null) return 0;
        if (previousAt == null || !currentAt.isAfter(previousAt)
                || previous.signum() < 0 || previous.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Invalid previous production sample");
        }
        if (resetTime != null && crossedReset(previousAt, currentAt, resetTime)) {
            return 0; // A new epoch starts here; the next valid sample contributes its delta.
        }
        if (current.compareTo(previous) >= 0) {
            return current.subtract(previous).intValueExact();
        }
        throw new IllegalArgumentException("Production counter decreased outside its configured reset time");
    }

    private boolean crossedReset(LocalDateTime before, LocalDateTime after, LocalTime resetTime) {
        LocalDate date = before.toLocalDate();
        LocalDateTime reset = date.atTime(resetTime);
        if (!reset.isAfter(before)) reset = reset.plusDays(1);
        return !reset.isAfter(after);
    }
}
