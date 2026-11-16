package com.sfaasproject.stf.equipment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sfaasproject.stf.equipment.service.EquipmentStatusDecisionService.Status;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EquipmentStatusTransitionServiceTest {
    private final EquipmentStatusTransitionService service = new EquipmentStatusTransitionService();
    private final LocalDateTime t = LocalDateTime.of(2026, 11, 16, 9, 0);

    @Test
    void unchangedStatusUpdatesLastReceiveWithoutNewHistory() {
        var previous = new EquipmentStatusTransitionService.Snapshot(Status.RUNNING, t, t);
        var decision = service.transition(previous, Status.RUNNING, t.plusSeconds(3), t.plusSeconds(3));
        assertNull(decision.historyChange());
        assertEquals(t, decision.state().statusSince());
        assertEquals(t.plusSeconds(3), decision.state().lastReceivedAt());
    }

    @Test
    void statusChangeClosesPreviousIntervalAndOpensNextAtSameInstant() {
        var previous = new EquipmentStatusTransitionService.Snapshot(Status.RUNNING, t, t);
        var decision = service.transition(previous, Status.FAULT, t.plusSeconds(3), t.plusSeconds(3));
        assertEquals(Status.RUNNING, decision.historyChange().closedStatus());
        assertEquals(t.plusSeconds(3), decision.historyChange().closedAt());
        assertEquals(Status.FAULT, decision.historyChange().openedStatus());
        assertEquals(t.plusSeconds(3), decision.historyChange().openedAt());
    }

    @Test
    void communicationLossKeepsActualLastReceiveTime() {
        var previous = new EquipmentStatusTransitionService.Snapshot(Status.RUNNING, t, t);
        var decision = service.transition(previous, Status.COMMUNICATION_LOST,
                t.plusSeconds(31), null);
        assertEquals(t, decision.state().lastReceivedAt());
        assertEquals(t.plusSeconds(31), decision.state().statusSince());
    }
}
