package com.sfaasproject.stf.measurement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ProductionCounterServiceTest {
    private final ProductionCounterService service = new ProductionCounterService();
    private final LocalDateTime start = LocalDateTime.of(2026, 11, 16, 23, 59);

    @Test
    void firstReadingIsBaselineAndIncreaseAddsOnlyDelta() {
        assertEquals(0, service.quantity(null, null, new BigDecimal("500"), start, LocalTime.MIDNIGHT));
        assertEquals(3, service.quantity(new BigDecimal("500"), start,
                new BigDecimal("503"), start.plusSeconds(3), LocalTime.MIDNIGHT));
    }

    @Test
    void decreaseAtResetEstablishesNewEpochWithoutAddingAbsoluteValue() {
        assertEquals(0, service.quantity(new BigDecimal("500"), start,
                new BigDecimal("2"), start.plusMinutes(2), LocalTime.MIDNIGHT));
    }

    @Test
    void resetEstablishesBaselineEvenIfNewAbsoluteValueIsLarger() {
        assertEquals(0, service.quantity(new BigDecimal("2"), start,
                new BigDecimal("10"), start.plusMinutes(2), LocalTime.MIDNIGHT));
    }

    @Test
    void unexpectedDecreaseIsTreatedAsResetWithoutProduction() {
        // PLC·Node-RED 재시작처럼 초기화 시각 밖에서 줄어든 카운터는 거절하지 않고 새 기준점으로 본다.
        LocalDateTime noon = LocalDateTime.of(2026, 11, 18, 12, 0);
        assertEquals(0, service.quantity(new BigDecimal("500"), noon, new BigDecimal("2"), noon.plusSeconds(3),
                LocalTime.MIDNIGHT));
        // 다음 값부터는 새 기준점에서 늘어난 만큼만 더한다.
        assertEquals(3, service.quantity(new BigDecimal("2"), noon.plusSeconds(3), new BigDecimal("5"),
                noon.plusSeconds(6), LocalTime.MIDNIGHT));
        assertEquals(0, service.quantity(new BigDecimal("500"), noon, new BigDecimal("0"), noon.plusSeconds(3),
                null));
    }

    @Test
    void rejectsFractionalCounter() {
        assertThrows(IllegalArgumentException.class, () -> service.quantity(
                new BigDecimal("500"), start, new BigDecimal("501.5"), start.plusSeconds(3),
                LocalTime.MIDNIGHT));
    }
}
