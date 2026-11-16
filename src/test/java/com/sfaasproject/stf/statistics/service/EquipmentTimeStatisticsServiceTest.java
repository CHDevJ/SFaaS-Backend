package com.sfaasproject.stf.statistics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sfaasproject.stf.equipment.service.EquipmentStatusDecisionService.Status;
import com.sfaasproject.stf.statistics.service.EquipmentTimeStatisticsService.Interval;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EquipmentTimeStatisticsServiceTest {
    private final EquipmentTimeStatisticsService service = new EquipmentTimeStatisticsService();
    private final LocalDateTime from = LocalDateTime.of(2026, 11, 17, 9, 0);

    @Test
    void clipsIntervalsAndCalculatesPlannedAvailability() {
        var durations = service.durations(List.of(
                new Interval(Status.RUNNING, from.minusMinutes(5), from.plusMinutes(30)),
                new Interval(Status.FAULT, from.plusMinutes(30), from.plusMinutes(40)),
                new Interval(Status.COMMUNICATION_LOST, from.plusMinutes(40), null)),
                from, from.plusMinutes(60));
        assertEquals(new BigDecimal("30.00"), durations.runningMinutes());
        assertEquals(new BigDecimal("10.00"), durations.faultMinutes());
        assertEquals(new BigDecimal("20.00"), durations.communicationLostMinutes());
        assertEquals(new BigDecimal("50.00"), service.availability(durations,
                new BigDecimal("60")).ratePercent());
    }

    @Test
    void rejectsMissingPlannedTime() {
        var durations = service.durations(List.of(), from, from.plusHours(1));
        assertThrows(IllegalArgumentException.class, () -> service.availability(durations,
                BigDecimal.ZERO));
    }
}
