package com.sfaasproject.stf.inventory.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.*;
import java.time.*;

class AssemblyForecastServiceTest {
    final AssemblyForecastService service = new AssemblyForecastService();
    final OffsetDateTime now = OffsetDateTime.parse("2026-11-17T10:00:00+09:00");

    AssemblyForecastService.Input input(long quantity, String supply, String consumption) {
        return new AssemblyForecastService.Input(
                quantity, new BigDecimal(supply), new BigDecimal(consumption), now, "READY");
    }

    AssemblyForecastService.Result calc(
            AssemblyForecastService.Input input, OffsetDateTime recovery, String price) {
        return service.calculate(
                input, 30, 10, recovery, new BigDecimal("360"), new BigDecimal(price));
    }

    @Test
    void decisionDocumentGoldenExampleIsSixMillionWon() {
        var r = calc(input(300, "0", "6"), now.plusMinutes(70), "50000");
        assertEquals(now.plusMinutes(50), r.stopAt());
        assertEquals(0, new BigDecimal("20").compareTo(r.shortageMinutes()));
        assertEquals(0, new BigDecimal("-20").compareTo(r.marginMinutes()));
        assertEquals(new BigDecimal("6000000"), r.loss());
    }

    @Test
    void zeroHistoryKeepsDepletionButHasNoInventedRecoveryOrAmount() {
        var r = calc(input(300, "0", "6"), null, "50000");
        assertEquals(now.plusMinutes(50), r.stopAt());
        assertEquals("NO_RECOVERY_HISTORY", r.forecastStatus());
        assertNull(r.marginMinutes());
        assertNull(r.shortageMinutes());
        assertNull(r.loss());
    }

    @Test
    void zeroPriceAndZeroShortageAreRealZeroAmounts() {
        assertEquals(BigDecimal.ZERO, calc(input(300, "0", "6"), now.plusMinutes(70), "0").loss());
        assertEquals(
                BigDecimal.ZERO, calc(input(300, "0", "6"), now.plusMinutes(40), "50000").loss());
    }

    @Test
    void nonpositiveNetHasNoDepletionAndNoLoss() {
        for (var supply : new String[] {"6", "8"}) {
            var r = calc(input(300, supply, "6"), now.plusMinutes(70), "50000");
            assertEquals("NO_DEPLETION", r.forecastStatus());
            assertEquals("MAINTAIN", r.stage());
            assertNull(r.stopAt());
            assertNull(r.loss());
        }
    }

    @Test
    void exactDangerWarningAndZeroStockBoundaries() {
        assertEquals("DANGER", calc(input(60, "0", "6"), null, "0").stage());
        assertEquals("CAUTION", calc(input(61, "0", "6"), null, "0").stage());
        assertEquals("CAUTION", calc(input(180, "0", "6"), null, "0").stage());
        assertEquals("MAINTAIN", calc(input(181, "0", "6"), null, "0").stage());
        assertEquals(now, calc(input(0, "0", "6"), null, "0").stopAt());
    }

    @Test
    void fractionalMinutesAreNotRoundedBeforeStopOrMoney() {
        var r =
                service.calculate(
                        input(1, "0", "4"),
                        30,
                        10,
                        now.plusSeconds(45),
                        new BigDecimal("60"),
                        new BigDecimal("1000"));
        assertEquals(now.plusSeconds(15), r.stopAt());
        assertEquals(new BigDecimal("500"), r.loss());
    }

    @Test
    void missingCounterStockOrPlanIsNotDisplayedAsZero() {
        for (var status :
                new String[] {
                    "INSUFFICIENT_DATA", "COUNTER_RESET_PENDING", "COUNTER_INCONSISTENT"
                }) {
            var r =
                    calc(
                            new AssemblyForecastService.Input(300L, null, null, now, status),
                            null,
                            "50000");
            assertEquals("INSUFFICIENT_DATA", r.forecastStatus());
            assertNull(r.stage());
            assertNull(r.stopAt());
        }
        assertEquals(
                "INSUFFICIENT_DATA",
                service.calculate(input(300, "0", "6"), 30, 10, null, null, BigDecimal.ZERO)
                        .forecastStatus());
    }

    @Test
    void dangerMustBeStrictlyLessThanWarning() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.calculate(
                                input(300, "0", "6"),
                                10,
                                10,
                                null,
                                BigDecimal.TEN,
                                BigDecimal.ZERO));
    }
}
