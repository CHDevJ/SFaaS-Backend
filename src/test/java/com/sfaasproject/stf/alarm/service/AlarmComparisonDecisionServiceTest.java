package com.sfaasproject.stf.alarm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sfaasproject.stf.alarm.service.AlarmComparisonDecisionService.Point;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlarmComparisonDecisionServiceTest {
    private final AlarmComparisonDecisionService service = new AlarmComparisonDecisionService();
    private final LocalDateTime occurred = LocalDateTime.of(2026, 11, 17, 9, 0);

    @Test
    void tenMinuteWindowsAndPercentChangeFollowOccurrenceBoundary() {
        var result = service.compare(occurred, List.of(
                new Point(occurred.plusMinutes(10), new BigDecimal("30")),
                new Point(occurred.minusMinutes(10), new BigDecimal("10")),
                new Point(occurred.minusMinutes(11), new BigDecimal("100")),
                new Point(occurred, new BigDecimal("20")),
                new Point(occurred.plusMinutes(11), new BigDecimal("100"))));
        assertEquals(1, result.before().size());
        assertEquals(2, result.after().size());
        assertEquals(new BigDecimal("10.0000"), result.beforeAverage());
        assertEquals(new BigDecimal("25.0000"), result.afterAverage());
        assertEquals(new BigDecimal("150.00"), result.changeRatePercent());
    }

    @Test
    void noSamplesOrZeroBaselineHaveNoChangeRate() {
        assertNull(service.compare(occurred, List.of()).changeRatePercent());
        var result = service.compare(occurred, List.of(
                new Point(occurred.minusMinutes(1), BigDecimal.ZERO),
                new Point(occurred, BigDecimal.ONE)));
        assertNull(result.changeRatePercent());
    }
}
