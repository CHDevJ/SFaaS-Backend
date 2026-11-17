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
    void rejectsUnexpectedDecreaseAndFractionalCounter() {
        assertThrows(IllegalArgumentException.class, () -> service.quantity(
                new BigDecimal("500"), start, new BigDecimal("2"), start.plusSeconds(3),
                LocalTime.MIDNIGHT));
        assertThrows(IllegalArgumentException.class, () -> service.quantity(
                new BigDecimal("500"), start, new BigDecimal("501.5"), start.plusSeconds(3),
                LocalTime.MIDNIGHT));
    }
}
