package com.sfaasproject.stf.system.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sfaasproject.stf.alarm.service.AlarmGrade;
import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.system.service.DataItemThresholdValidationService.Direction;
import com.sfaasproject.stf.system.service.DataItemThresholdValidationService.Measurement;
import com.sfaasproject.stf.system.service.DataItemThresholdValidationService.State;
import com.sfaasproject.stf.system.service.DataItemThresholdValidationService.ValueType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DataItemThresholdValidationServiceTest {
    private final DataItemThresholdValidationService service =
            new DataItemThresholdValidationService();

    @Test
    void measurementDirectionRequiresCautionBeforeCritical() {
        assertDoesNotThrow(() -> service.validate(ValueType.MEASUREMENT,
                new Measurement(new BigDecimal("10"), new BigDecimal("20"),
                        Direction.ABOVE), null));
        assertDoesNotThrow(() -> service.validate(ValueType.MEASUREMENT,
                new Measurement(new BigDecimal("20"), new BigDecimal("10"),
                        Direction.BELOW), null));
        var error = assertThrows(DomainException.class, () -> service.validate(
                ValueType.MEASUREMENT, new Measurement(BigDecimal.TEN, BigDecimal.TEN,
                        Direction.ABOVE), null));
        assertEquals("INVALID_THRESHOLD_ORDER", error.code());
    }

    @Test
    void stateUsesBinaryValueAndCumulativeHasNoAlarmThreshold() {
        assertDoesNotThrow(() -> service.validate(ValueType.STATE, null,
                new State(0, AlarmGrade.CRITICAL)));
        assertEquals("VALIDATION_ERROR", assertThrows(DomainException.class,
                () -> service.validate(ValueType.STATE, null,
                        new State(2, AlarmGrade.CAUTION))).code());
        assertEquals("VALIDATION_ERROR", assertThrows(DomainException.class,
                () -> service.validate(ValueType.CUMULATIVE,
                        new Measurement(BigDecimal.ONE, BigDecimal.TEN, Direction.ABOVE),
                        null)).code());
        assertDoesNotThrow(() -> service.validate(ValueType.CUMULATIVE, null, null));
    }
}
