package com.sfaasproject.stf.statistics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** Loss avoidance from fault recovery events and the documented per-hour output formula. */
@Service
public class LossAvoidanceStatisticsService {
    public record Recovery(LocalDate date, boolean recoveredBeforeStop,
            long expectedDowntimeMinutes, BigDecimal hourlyProduction,
            BigDecimal unitPrice) { }
    public record Summary(BigDecimal todayAvoidedAmount, BigDecimal previousDayDifference,
            BigDecimal monthlyAvoidedAmount, int todayFaultCount,
            int recoveredBeforeStopCount, double recoveryRate) { }

    public Summary summarize(List<Recovery> recoveries, LocalDate today) {
        if (recoveries == null || today == null) {
            throw new IllegalArgumentException("Recoveries and today are required");
        }
        BigDecimal todayAmount = BigDecimal.ZERO;
        BigDecimal previousAmount = BigDecimal.ZERO;
        BigDecimal monthlyAmount = BigDecimal.ZERO;
        int todayCount = 0;
        int earlyCount = 0;
        for (Recovery recovery : recoveries) {
            validate(recovery);
            BigDecimal avoided = recovery.recoveredBeforeStop() ? amount(recovery) : BigDecimal.ZERO;
            if (recovery.date().equals(today)) {
                todayCount++;
                if (recovery.recoveredBeforeStop()) earlyCount++;
                todayAmount = todayAmount.add(avoided);
            }
            if (recovery.date().equals(today.minusDays(1))) {
                previousAmount = previousAmount.add(avoided);
            }
            if (recovery.date().getYear() == today.getYear()
                    && recovery.date().getMonth() == today.getMonth()
                    && !recovery.date().isAfter(today)) {
                monthlyAmount = monthlyAmount.add(avoided);
            }
        }
        return new Summary(todayAmount, todayAmount.subtract(previousAmount),
                monthlyAmount, todayCount, earlyCount,
                todayCount == 0 ? 0 : 100.0 * earlyCount / todayCount);
    }

    private BigDecimal amount(Recovery recovery) {
        return BigDecimal.valueOf(recovery.expectedDowntimeMinutes())
                .multiply(recovery.hourlyProduction()).multiply(recovery.unitPrice())
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
    }

    private void validate(Recovery recovery) {
        if (recovery == null || recovery.date() == null || recovery.expectedDowntimeMinutes() < 0
                || recovery.hourlyProduction() == null || recovery.hourlyProduction().signum() < 0
                || recovery.unitPrice() == null || recovery.unitPrice().signum() < 0) {
            throw new IllegalArgumentException("Invalid recovery record");
        }
    }
}
