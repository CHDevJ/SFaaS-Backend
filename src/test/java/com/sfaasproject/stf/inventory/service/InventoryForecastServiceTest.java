package com.sfaasproject.stf.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sfaasproject.stf.inventory.service.InventoryForecastService.Stage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InventoryForecastServiceTest {
    private final InventoryForecastService service = new InventoryForecastService();
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 17, 9, 0);

    @Test
    void netConsumptionPredictsFortyFiveMinutes() {
        var result = service.forecast(90, 80, 100, now, 30, 10);
        assertEquals(new BigDecimal("2.0000"), result.netConsumptionPerMinute());
        assertEquals(new BigDecimal("45.0000"), result.remainingMinutes());
        assertEquals(now.plusMinutes(45), result.expectedDepletionAt());
        assertEquals(Stage.MAINTAIN, result.stage());
    }

    @Test
    void exactThirtyAndTenMinuteBoundariesUseCautionAndDanger() {
        assertEquals(Stage.CAUTION, service.forecast(60, 80, 100, now, 30, 10).stage());
        assertEquals(Stage.DANGER, service.forecast(20, 80, 100, now, 30, 10).stage());
        assertEquals(Stage.MAINTAIN, service.forecast(61, 80, 100, now, 30, 10).stage());
    }

    @Test
    void noNetConsumptionHasNoDepletionTime() {
        var result = service.forecast(20, 100, 80, now, 30, 10);
        assertNull(result.remainingMinutes());
        assertNull(result.expectedDepletionAt());
        assertEquals(Stage.MAINTAIN, result.stage());
    }

    @Test
    void stockCannotBecomeNegative() {
        assertEquals(0, service.nextQuantity(2, 0, 4));
        assertEquals(7, service.nextQuantity(10, 2, 5));
        assertThrows(IllegalArgumentException.class, () -> service.nextQuantity(1, -1, 0));
    }
}
