package com.sfaasproject.stf.statistics.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

class ActualLossCalculatorTest {
    final ActualLossCalculator calculator = new ActualLossCalculator();
    final OffsetDateTime at = OffsetDateTime.parse("2026-11-18T10:00:00+09:00");

    ActualLossCalculator.Interval interval(long seconds, long hourly, long price) {
        return new ActualLossCalculator.Interval(
                at, at.plusSeconds(seconds), BigDecimal.valueOf(hourly), BigDecimal.valueOf(price));
    }

    @Test
    void twentyMinutesAtConfiguredOutputAndPriceIsSixMillionWon() {
        assertEquals(
                new BigDecimal("6000000"),
                calculator.calculate(List.of(interval(1200, 360, 50000))));
    }

    @Test
    void roundsOnlyAfterSummingSubWonIntervals() {
        assertEquals(
                BigDecimal.ONE,
                calculator.calculate(List.of(interval(1, 1080, 1), interval(1, 1080, 1))));
    }

    @Test
    void emptyIntervalsAndZeroPriceHaveRealZeroLoss() {
        assertEquals(BigDecimal.ZERO, calculator.calculate(List.of()));
        assertEquals(BigDecimal.ZERO, calculator.calculate(List.of(interval(1200, 360, 0))));
    }

    @Test
    void sameDurationWithDifferentSnapshotsUsesEachOriginalSnapshot() {
        assertEquals(
                new BigDecimal("3"),
                calculator.calculate(List.of(interval(60, 60, 1), interval(60, 60, 2))));
    }

    @Test
    void reversedTimeAndNegativeSnapshotAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(List.of(interval(-1, 360, 1))));
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(List.of(interval(1, 360, -1))));
    }

    @Test
    void subSecondDurationsAreNotRoundedBeforeMoney() {
        var i =
                new ActualLossCalculator.Interval(
                        at, at.plusNanos(500_000_000), new BigDecimal("3600"), BigDecimal.ONE);
        assertEquals(BigDecimal.ONE, calculator.calculate(List.of(i, i)));
    }
}
