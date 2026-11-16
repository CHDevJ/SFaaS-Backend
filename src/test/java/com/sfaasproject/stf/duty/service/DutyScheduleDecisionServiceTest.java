package com.sfaasproject.stf.duty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sfaasproject.stf.common.DomainException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DutyScheduleDecisionServiceTest {
    private final DutyScheduleDecisionService service = new DutyScheduleDecisionService();
    private final LocalDate monday = LocalDate.of(2026, 11, 16);

    @Test
    void consecutiveInclusivePeriodsCoverEveryDay() {
        service.validate(List.of(schedule(1, 0, 6), schedule(2, 7, 13)),
                monday, monday.plusDays(13), Set.of(10L));
    }

    @Test
    void coverageMayStartBeforeRequestedWindow() {
        service.validate(List.of(new DutyScheduleDecisionService.Schedule(1,
                monday.minusDays(2), monday.plusDays(6), Set.of(10L))),
                monday, monday.plusDays(6), Set.of(10L));
    }

    @Test
    void oneDayOverlapAndGapAreRejected() {
        assertEquals("DUTY_OVERLAP", assertThrows(DomainException.class, () ->
                service.validate(List.of(schedule(1, 0, 6), schedule(2, 6, 13)),
                        monday, monday.plusDays(13), Set.of(10L))).code());
        assertEquals("DUTY_GAP_NOT_ALLOWED", assertThrows(DomainException.class, () ->
                service.validate(List.of(schedule(1, 0, 6), schedule(2, 8, 13)),
                        monday, monday.plusDays(13), Set.of(10L))).code());
    }

    @Test
    void assigneeMustBeActiveMaintenanceAndLastFutureDeletionOnly() {
        assertThrows(DomainException.class, () -> service.validate(
                List.of(schedule(1, 0, 6)), monday, monday.plusDays(6), Set.of(20L)));
        var schedules = List.of(schedule(1, 0, 6), schedule(2, 7, 13));
        assertTrue(service.canDeleteLastFutureSchedule(2, monday, schedules));
        assertFalse(service.canDeleteLastFutureSchedule(1, monday, schedules));
    }

    @Test
    void calendarIncludesDaysAtBothScheduleBoundaries() {
        var days = service.calendar(YearMonth.of(2026, 11),
                List.of(schedule(1, 0, 6), schedule(2, 7, 13)));
        assertEquals(30, days.size());
        assertEquals(1L, days.get(15).scheduleId());
        assertEquals(2L, days.get(22).scheduleId());
        assertTrue(days.get(0).assigneeIds().isEmpty());
    }

    private DutyScheduleDecisionService.Schedule schedule(long id, int startOffset, int endOffset) {
        return new DutyScheduleDecisionService.Schedule(id, monday.plusDays(startOffset),
                monday.plusDays(endOffset), Set.of(10L));
    }
}
