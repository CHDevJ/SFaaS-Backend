package com.sfaasproject.stf.equipment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EquipmentStatusDecisionServiceTest {
    private final EquipmentStatusDecisionService service = new EquipmentStatusDecisionService();
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 16, 9, 0);

    @Test
    void appliesPriorityFromCommunicationThroughRunning() {
        assertEquals(EquipmentStatusDecisionService.Status.COMMUNICATION_LOST,
                service.decide(now.minusSeconds(31), now, true, true, true));
        assertEquals(EquipmentStatusDecisionService.Status.FAULT,
                service.decide(now, now, true, true, false));
        assertEquals(EquipmentStatusDecisionService.Status.CAUTION,
                service.decide(now, now, false, true, false));
        assertEquals(EquipmentStatusDecisionService.Status.IDLE,
                service.decide(now, now, false, false, false));
        assertEquals(EquipmentStatusDecisionService.Status.RUNNING,
                service.decide(now, now, false, false, true));
    }

    @Test
    void configuredCommunicationTimeoutIsApplied() {
        assertEquals(EquipmentStatusDecisionService.Status.COMMUNICATION_LOST,
                service.decide(now.minusSeconds(11), now, false, false, true,
                        java.time.Duration.ofSeconds(10)));
    }

    @Test
    void thirtySecondsExactlyIsStillConnected() {
        assertEquals(EquipmentStatusDecisionService.Status.RUNNING,
                service.decide(now.minusSeconds(30), now, false, false, true));
    }
}
