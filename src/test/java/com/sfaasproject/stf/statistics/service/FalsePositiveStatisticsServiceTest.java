package com.sfaasproject.stf.statistics.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FalsePositiveStatisticsServiceTest {
    private final FalsePositiveStatisticsService service = new FalsePositiveStatisticsService();
    private final LocalDateTime time = LocalDateTime.of(2026, 11, 17, 9, 0);

    @Test
    void groupsCountsRatesAndResponseTimes() {
        var alarms = List.of(
                new FalsePositiveStatisticsService.Alarm(1, "A", 10, "X", time,
                        time.plusMinutes(2), true),
                new FalsePositiveStatisticsService.Alarm(1, "A", 10, "X", time,
                        time.plusMinutes(4), false),
                new FalsePositiveStatisticsService.Alarm(2, "B", 20, "Y", time,
                        time.plusMinutes(6), true));
        var result = service.summarize(alarms);
        assertEquals(2, result.count());
        assertEquals(200.0 / 3, result.rate());
        assertEquals(4.0, result.responseMinutes());
        assertEquals(1L, result.equipmentRanking().get(0).id());
        assertEquals(100.0, result.dataItemRates().get(0).rate());
        assertEquals(50.0, result.dataItemRates().get(1).rate());
    }

    @Test
    void handlesEmptyRecords() {
        var result = service.summarize(List.of());
        assertEquals(0, result.count());
        assertEquals(0.0, result.rate());
        assertTrue(result.equipmentRanking().isEmpty());
    }
}
