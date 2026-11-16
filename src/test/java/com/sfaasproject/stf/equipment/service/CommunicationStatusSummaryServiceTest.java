package com.sfaasproject.stf.equipment.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommunicationStatusSummaryServiceTest {
    private final CommunicationStatusSummaryService service =
            new CommunicationStatusSummaryService();
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 17, 9, 0);

    @Test
    void allDisconnectedStartsWhenLastEquipmentDrops() {
        var result = service.summarize(List.of(
                new CommunicationStatusSummaryService.Equipment(1, true, now.minusMinutes(3)),
                new CommunicationStatusSummaryService.Equipment(2, true, now.minusMinutes(1))));
        assertEquals(CommunicationStatusSummaryService.Status.ALL_DISCONNECTED, result.status());
        assertEquals(2, result.disconnectedEquipmentCount());
        assertEquals(now.minusMinutes(1), result.allDisconnectedSince());
    }

    @Test
    void partialAndEmptySnapshots() {
        var result = service.summarize(List.of(
                new CommunicationStatusSummaryService.Equipment(1, true, now),
                new CommunicationStatusSummaryService.Equipment(2, false, null)));
        assertEquals(CommunicationStatusSummaryService.Status.PARTIAL_DISCONNECTED,
                result.status());
        assertNull(result.allDisconnectedSince());
        assertEquals(CommunicationStatusSummaryService.Status.ALL_NORMAL,
                service.summarize(List.of()).status());
    }
}
