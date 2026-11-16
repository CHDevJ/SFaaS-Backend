package com.sfaasproject.stf.statistics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sfaasproject.stf.alarm.service.AlarmGrade;
import com.sfaasproject.stf.statistics.service.AlarmCountStatisticsService.Alarm;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlarmCountStatisticsServiceTest {
    private final AlarmCountStatisticsService service = new AlarmCountStatisticsService();
    private final LocalDate day = LocalDate.of(2026, 11, 17);

    @Test
    void dailyBucketsIncludeEmptyDaysAndFalsePositiveOverlap() {
        var alarms = List.of(new Alarm(day.atTime(9, 0), AlarmGrade.CRITICAL, false),
                new Alarm(day.atTime(10, 0), AlarmGrade.CAUTION, true));
        var result = service.daily(alarms, day, day.plusDays(1));
        assertEquals(new AlarmCountStatisticsService.Daily(day, 1, 1, 1), result.get(0));
        assertEquals(new AlarmCountStatisticsService.Daily(day.plusDays(1), 0, 0, 0), result.get(1));
        assertEquals(50.0, service.falsePositives(alarms).ratePercent());
        assertEquals(0.0, service.falsePositives(List.of()).ratePercent());
    }
}
