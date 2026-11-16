package com.sfaasproject.stf.alarm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.ProcessStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlarmListDecisionServiceTest {
    private final AlarmListDecisionService service = new AlarmListDecisionService();
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 16, 9, 10);

    @Test
    void ordersUnacknowledgedThenCriticalThenNewestAndExcludesClosed() {
        var alarms = List.of(
                entry(1, AlarmGrade.CRITICAL, ProcessStatus.ACKNOWLEDGED, 9),
                entry(2, AlarmGrade.CAUTION, ProcessStatus.UNACKNOWLEDGED, 8),
                entry(3, AlarmGrade.CRITICAL, ProcessStatus.UNACKNOWLEDGED, 7),
                entry(4, AlarmGrade.CRITICAL, ProcessStatus.UNACKNOWLEDGED, 9),
                entry(5, AlarmGrade.CRITICAL, ProcessStatus.CLOSED, 10));
        assertEquals(List.of(4L, 3L, 2L, 1L), service.order(alarms, now).stream()
                .map(d -> d.alarm().alarmId()).toList());
    }

    @Test
    void elapsedNeverNegativeAndDelayAppliesOnlyToUnacknowledged() {
        var result = service.order(List.of(
                entry(1, AlarmGrade.CAUTION, ProcessStatus.UNACKNOWLEDGED, 7),
                entry(2, AlarmGrade.CAUTION, ProcessStatus.ACKNOWLEDGED, 7),
                new AlarmListDecisionService.Entry(3, AlarmGrade.CAUTION,
                        ProcessStatus.UNACKNOWLEDGED, now.plusSeconds(1))), now);
        var byId = result.stream().collect(java.util.stream.Collectors.toMap(
                display -> display.alarm().alarmId(), display -> display));
        assertTrue(byId.get(1L).acknowledgementDelayed());
        assertFalse(byId.get(2L).acknowledgementDelayed());
        assertEquals(0, byId.get(3L).elapsedSeconds());
    }

    private AlarmListDecisionService.Entry entry(long id, AlarmGrade grade,
            ProcessStatus status, int minute) {
        return new AlarmListDecisionService.Entry(id, grade, status,
                LocalDateTime.of(2026, 11, 16, 9, minute));
    }
}
