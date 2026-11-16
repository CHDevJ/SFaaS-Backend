package com.sfaasproject.stf.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sfaasproject.stf.inventory.service.AssemblyLossDecisionService.LineForecast;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssemblyLossDecisionServiceTest {
    private final AssemblyLossDecisionService service = new AssemblyLossDecisionService();
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 17, 9, 0);

    @Test
    void earliestLineStopsFirstAndFifteenMinuteDelayCausesLoss() {
        var result = service.forecast(List.of(
                new LineForecast(1, now.plusMinutes(45)),
                new LineForecast(2, now.plusMinutes(30))),
                now, 45, new BigDecimal("60"), new BigDecimal("1000"));
        assertEquals(2L, result.limitingLineId());
        assertEquals(new BigDecimal("-15.00"), result.marginMinutes());
        assertEquals(new BigDecimal("15000"), result.estimatedLoss());
    }

    @Test
    void recoveryBeforeStopAndMaintainedStockHaveZeroLoss() {
        var safe = service.forecast(List.of(new LineForecast(1, now.plusMinutes(30))),
                now, 10, new BigDecimal("60"), new BigDecimal("1000"));
        assertEquals(new BigDecimal("20.00"), safe.marginMinutes());
        assertEquals(BigDecimal.ZERO, safe.estimatedLoss());
        var maintained = service.forecast(List.of(new LineForecast(1, null)),
                now, 10, new BigDecimal("60"), new BigDecimal("1000"));
        assertNull(maintained.stopExpectedAt());
        assertEquals(BigDecimal.ZERO, maintained.estimatedLoss());
    }

    @Test
    void avoidedLossUsesRecoveredBeforeStopCount() {
        assertEquals(new BigDecimal("60000"), service.avoidedLoss(2, 30,
                new BigDecimal("60"), new BigDecimal("1000")));
    }
}
