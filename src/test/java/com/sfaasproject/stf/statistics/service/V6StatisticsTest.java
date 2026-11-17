package com.sfaasproject.stf.statistics.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sfaasproject.stf.alarm.service.AlarmGrade;
import com.sfaasproject.stf.alarm.service.AlarmLifecycleService.ProcessStatus;
import com.sfaasproject.stf.statistics.service.ClosedAlarmStatisticsService.*;
import com.sfaasproject.stf.statistics.service.StopCauseStatisticsService.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

class V6StatisticsTest {
    final OffsetDateTime from = OffsetDateTime.parse("2026-11-17T12:00:00+09:00"),
            to = from.plusHours(1);
    final ClosedAlarmStatisticsService alarms = new ClosedAlarmStatisticsService();
    final StopCauseStatisticsService stops = new StopCauseStatisticsService();

    Alarm closed(long id, int start, int duration) {
        return new Alarm(
                id,
                1,
                AlarmGrade.CRITICAL,
                ProcessStatus.CLOSED,
                from.plusMinutes(start),
                from.plusMinutes(start + duration));
    }

    @Test
    void onlyClosedDurationsCountAndUpperBoundaryIsExcluded() {
        var r =
                alarms.response(
                        List.of(
                                closed(1, 0, 5),
                                closed(2, 20, 15),
                                closed(3, 60, 20),
                                new Alarm(
                                        4,
                                        1,
                                        AlarmGrade.CAUTION,
                                        ProcessStatus.ACTIVE,
                                        from.plusMinutes(2),
                                        null)),
                        from,
                        to);
        assertEquals(3, r.totalCount());
        assertEquals(1, r.activeCount());
        assertEquals(2, r.criticalCount());
        assertEquals(0, BigDecimal.TEN.compareTo(r.averageActionMinutes()));
        assertEquals(0, new BigDecimal("15").compareTo(r.longestActionMinutes()));
    }

    @Test
    void noSamplesReturnNullAndMtbfUsesAdjacentFaultOccurrences() {
        assertNull(alarms.response(List.of(), from, to).averageActionMinutes());
        assertNull(alarms.reliability(1, List.of(closed(1, 0, 5)), from, to).mtbfMinutes());
        var r =
                alarms.reliability(
                        1, List.of(closed(3, 50, 5), closed(1, 0, 5), closed(2, 20, 5)), from, to);
        assertEquals(0, new BigDecimal("25").compareTo(r.mtbfMinutes()));
        assertEquals(0, new BigDecimal("5").compareTo(r.mttrMinutes()));
    }

    @Test
    void communicationWinsThenEarliestFaultAndEachEquipmentCountsSeparately() {
        var r =
                stops.summarize(
                        List.of(
                                new Stop(
                                        1,
                                        1,
                                        Source.ALARM,
                                        "최초 고장",
                                        from.minusMinutes(10),
                                        from.plusMinutes(40)),
                                new Stop(1, 2, Source.ALARM, "중복 고장", from.plusMinutes(5), to),
                                new Stop(
                                        1,
                                        3,
                                        Source.COMMUNICATION_LOST,
                                        "통신 끊김",
                                        from.plusMinutes(10),
                                        from.plusMinutes(20)),
                                new Stop(2, 4, Source.ALARM, "별도 설비", from, null)),
                        from,
                        to);
        assertEquals(4, r.size());
        assertEquals(
                0,
                new BigDecimal("120")
                        .compareTo(
                                r.stream()
                                        .map(Cause::downtimeMinutes)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(
                0,
                new BigDecimal("30")
                        .compareTo(
                                r.stream()
                                        .filter(c -> c.cause().equals("최초 고장"))
                                        .findFirst()
                                        .orElseThrow()
                                        .downtimeMinutes()));
        assertEquals(
                0,
                new BigDecimal("10")
                        .compareTo(
                                r.stream()
                                        .filter(c -> c.source() == Source.COMMUNICATION_LOST)
                                        .findFirst()
                                        .orElseThrow()
                                        .downtimeMinutes()));
    }

    @Test
    void duplicateIntervalsAndTouchingHalfOpenBoundariesDoNotDoubleCount() {
        var s = new Stop(1, 1, Source.ALARM, "고장", from, to);
        var r =
                stops.summarize(
                        List.of(s, s, new Stop(1, 2, Source.ALARM, "기간 밖", to, to.plusHours(1))),
                        from,
                        to);
        assertEquals(1, r.size());
        assertEquals(0, new BigDecimal("60").compareTo(r.getFirst().downtimeMinutes()));
    }
}
