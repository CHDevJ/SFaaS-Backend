package com.sfaasproject.stf.alarm.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.*;
import java.time.*;
import java.util.*;

class RecoveryTimeEstimatorTest {
    final RecoveryTimeEstimator service = new RecoveryTimeEstimator();
    final OffsetDateTime fault = OffsetDateTime.parse("2026-11-17T12:00:00+09:00");

    RecoveryTimeEstimator.ClosedAlarm past(
            long equipment, long item, AlarmGrade grade, int minutes) {
        return new RecoveryTimeEstimator.ClosedAlarm(
                equipment,
                item,
                grade,
                fault.minusDays(1),
                fault.minusDays(1).plusMinutes(minutes));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void usesEveryValidSameItemCriticalSampleWithNoFallback(int count) {
        var history = new ArrayList<RecoveryTimeEstimator.ClosedAlarm>();
        history.add(past(1, 99, AlarmGrade.CRITICAL, 200));
        history.add(past(2, 2, AlarmGrade.CRITICAL, 300));
        history.add(past(1, 2, AlarmGrade.CAUTION, 400));
        for (int i = 0; i < count; i++) history.add(past(1, 2, AlarmGrade.CRITICAL, 10 + 10 * i));
        var result = service.estimate(1, 2, fault, history);
        assertEquals(count, result.repairHistoryCount());
        if (count == 0) {
            assertNull(result.averageRepairMinutes());
            assertNull(result.recoveryExpectedAt());
        } else {
            assertEquals(
                    0,
                    BigDecimal.valueOf(10 + 5L * (count - 1))
                            .compareTo(result.averageRepairMinutes()));
            assertEquals(fault.plusMinutes(10 + 5L * (count - 1)), result.recoveryExpectedAt());
        }
    }

    @Test
    void excludesOpenNegativeFutureAndClosureExactlyAtFault() {
        var history =
                List.of(
                        new RecoveryTimeEstimator.ClosedAlarm(
                                1, 2, AlarmGrade.CRITICAL, fault.minusMinutes(1), null),
                        new RecoveryTimeEstimator.ClosedAlarm(
                                1, 2, AlarmGrade.CRITICAL, fault.minusMinutes(1), fault),
                        new RecoveryTimeEstimator.ClosedAlarm(
                                1,
                                2,
                                AlarmGrade.CRITICAL,
                                fault.minusMinutes(1),
                                fault.plusMinutes(1)),
                        new RecoveryTimeEstimator.ClosedAlarm(
                                1,
                                2,
                                AlarmGrade.CRITICAL,
                                fault.minusMinutes(1),
                                fault.minusMinutes(2)));
        assertEquals(0, service.estimate(1, 2, fault, history).repairHistoryCount());
    }

    @Test
    void zeroDurationIsAValidSampleAndOffsetsUseInstants() {
        var a =
                new RecoveryTimeEstimator.ClosedAlarm(
                        1,
                        2,
                        AlarmGrade.CRITICAL,
                        fault.minusHours(1),
                        fault.minusHours(1).withOffsetSameInstant(ZoneOffset.UTC));
        var r = service.estimate(1, 2, fault, List.of(a));
        assertEquals(1, r.repairHistoryCount());
        assertEquals(0, r.averageRepairMinutes().signum());
        assertEquals(fault, r.recoveryExpectedAt());
    }
}
