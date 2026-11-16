package com.sfaasproject.stf.equipment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CurrentDataItemDecisionServiceTest {
    private final CurrentDataItemDecisionService service = new CurrentDataItemDecisionService();
    private final LocalDateTime at = LocalDateTime.of(2026, 11, 16, 9, 0);

    @Test
    void measurementShowsConfiguredPrecisionAndThresholds() {
        var item = new CurrentDataItemDecisionService.Item(1, "온도", "MEASUREMENT", "°C",
                1, new BigDecimal("80"), new BigDecimal("90"), null, null);
        var result = service.map(item, new BigDecimal("85.26"), "CAUTION", at);
        assertEquals("85.3", result.displayValue());
        assertEquals("CAUTION", result.valueStatus());
        assertEquals(new BigDecimal("90"), result.criticalThreshold());
    }

    @Test
    void stateShowsConfiguredLabelAndCumulativeHasNoGrade() {
        var state = new CurrentDataItemDecisionService.Item(2, "가동", "STATE", null,
                0, null, null, "정지", "가동");
        assertEquals("가동", service.map(state, BigDecimal.ONE, "NORMAL", at).displayValue());
        var counter = new CurrentDataItemDecisionService.Item(3, "생산 수", "CUMULATIVE", "개",
                0, null, null, null, null);
        var result = service.map(counter, new BigDecimal("123"), "NORMAL", at);
        assertEquals("123", result.displayValue());
        assertNull(result.valueStatus());
    }
}
