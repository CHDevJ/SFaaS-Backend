package com.sfaasproject.stf.statistics.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlarmHistoryMetricsServiceTest {
    private final AlarmHistoryMetricsService service = new AlarmHistoryMetricsService();
    private final LocalDateTime start = LocalDateTime.of(2026, 11, 17, 9, 0);

    @Test
    void averagesUseOnlyAvailableTimestampsAndExcludeFalsePositiveRepair() {
        var alarms = List.of(
                new AlarmHistoryMetricsService.Alarm(start, start.plusMinutes(5),
                        start.plusMinutes(30), true, false),
                new AlarmHistoryMetricsService.Alarm(start, start.plusMinutes(15),
                        start.plusMinutes(90), false, true),
                new AlarmHistoryMetricsService.Alarm(start, null, null, false, false));
        var result = service.response(alarms);
        assertEquals(10.0, result.averageAcknowledgementMinutes());
        assertEquals(30.0, result.averageRepairMinutes());
        assertEquals(1, result.delayedAlarmCount());
        assertEquals(100.0 / 3, result.delayedAlarmRate());
    }

    @Test
    void emptyInputAndInvalidChronology() {
        assertEquals(0, service.response(List.of()).delayedAlarmCount());
        assertThrows(IllegalArgumentException.class, () -> service.response(List.of(
                new AlarmHistoryMetricsService.Alarm(start, start.minusSeconds(1),
                        null, false, false))));
    }
}
