package com.sfaasproject.stf.system.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SystemSettingsValidationServiceTest {
    private final SystemSettingsValidationService service = new SystemSettingsValidationService();

    @Test
    void acceptsOpenApiBoundsAndZeroUnitPrice() {
        var values = new SystemSettingsValidationService.Judgement(
                10, 1, 5, 1, 1, 11, 10);
        assertSame(values, service.validate(values));
        assertEquals(BigDecimal.ZERO, service.validateUnitPrice(BigDecimal.ZERO));
        assertDoesNotThrow(() -> service.validate(new SystemSettingsValidationService.Judgement(
                300, 60, 100, 10, 10, 120, 1)));
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> service.validate(
                new SystemSettingsValidationService.Judgement(9, 1, 5, 1, 1, 30, 10)));
        assertThrows(IllegalArgumentException.class, () -> service.validate(
                new SystemSettingsValidationService.Judgement(30, 1, 101, 1, 1, 30, 10)));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateUnitPrice(new BigDecimal("-1")));
    }
}
