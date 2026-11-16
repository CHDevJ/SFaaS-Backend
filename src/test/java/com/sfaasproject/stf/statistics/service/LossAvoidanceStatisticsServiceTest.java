package com.sfaasproject.stf.statistics.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LossAvoidanceStatisticsServiceTest {
    private final LossAvoidanceStatisticsService service = new LossAvoidanceStatisticsService();
    private final LocalDate today = LocalDate.of(2026, 11, 17);

    @Test
    void aggregatesTodayPreviousDayAndMonthWithoutFutureEvents() {
        var events = List.of(
                new LossAvoidanceStatisticsService.Recovery(today, true, 30,
                        new BigDecimal("10"), new BigDecimal("100")),
                new LossAvoidanceStatisticsService.Recovery(today, false, 20,
                        new BigDecimal("10"), new BigDecimal("100")),
                new LossAvoidanceStatisticsService.Recovery(today.minusDays(1), true, 60,
                        new BigDecimal("10"), new BigDecimal("100")),
                new LossAvoidanceStatisticsService.Recovery(today.plusDays(1), true, 60,
                        new BigDecimal("10"), new BigDecimal("100")));
        var result = service.summarize(events, today);
        assertEquals(new BigDecimal("500"), result.todayAvoidedAmount());
        assertEquals(new BigDecimal("-500"), result.previousDayDifference());
        assertEquals(new BigDecimal("1500"), result.monthlyAvoidedAmount());
        assertEquals(2, result.todayFaultCount());
        assertEquals(1, result.recoveredBeforeStopCount());
        assertEquals(50.0, result.recoveryRate());
    }

    @Test
    void emptyRecordsYieldZeroSummary() {
        var result = service.summarize(List.of(), today);
        assertEquals(BigDecimal.ZERO, result.todayAvoidedAmount());
        assertEquals(0.0, result.recoveryRate());
    }
}
