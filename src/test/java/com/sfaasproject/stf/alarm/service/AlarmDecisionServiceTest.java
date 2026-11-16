package com.sfaasproject.stf.alarm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AlarmDecisionServiceTest {
    private final AlarmDecisionService service = new AlarmDecisionService();
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 16, 9, 0);

    @Test
    void occursAfterThreeConsecutiveCautionValuesAtInclusiveLimit() {
        var state = AlarmEvaluationState.empty();
        state = accept(measure(0, "10", AlarmReading.Direction.ABOVE, "10", "20"), state).state();
        state = accept(measure(1, "9", AlarmReading.Direction.ABOVE, "10", "20"), state).state();
        state = accept(measure(2, "10", AlarmReading.Direction.ABOVE, "10", "20"), state).state();
        state = accept(measure(3, "15", AlarmReading.Direction.ABOVE, "10", "20"), state).state();
        var result = accept(measure(4, "10", AlarmReading.Direction.ABOVE, "10", "20"), state);
        assertEquals(AlarmEvaluationResult.Transition.OCCUR, result.transition());
        assertEquals(AlarmGrade.CAUTION, result.eventGrade());
        assertEquals(new BigDecimal("10"), result.matchedLimit());
        assertEquals(3, result.state().outStreak());
    }

    @Test
    void createsCriticalAlarmAfterThreeCriticalReadings() {
        var state = AlarmEvaluationState.empty();
        state = accept(measure(0, "20", AlarmReading.Direction.ABOVE, "10", "20"), state).state();
        state = accept(measure(1, "22", AlarmReading.Direction.ABOVE, "10", "20"), state).state();
        var result = accept(measure(2, "21", AlarmReading.Direction.ABOVE, "10", "20"), state);
        assertEquals(AlarmEvaluationResult.Transition.OCCUR, result.transition());
        assertEquals(AlarmGrade.CRITICAL, result.eventGrade());
        assertEquals(new BigDecimal("20"), result.matchedLimit());
    }

    @Test
    void mixedOutValuesCreateCautionThenThreeCriticalValuesGradeUpOnce() {
        var state = AlarmEvaluationState.empty();
        state = accept(measure(0, "10", AlarmReading.Direction.ABOVE, "10", "20"), state).state();
        state = accept(measure(1, "20", AlarmReading.Direction.ABOVE, "10", "20"), state).state();
        var occur = accept(measure(2, "11", AlarmReading.Direction.ABOVE, "10", "20"), state);
        assertEquals(AlarmGrade.CAUTION, occur.eventGrade());
        state = occur.state();
        for (int i = 3; i <= 4; i++) {
            var result = accept(measure(i, "20", AlarmReading.Direction.ABOVE, "10", "20"), state);
            assertEquals(AlarmEvaluationResult.Transition.NONE, result.transition());
            state = result.state();
        }
        var gradeUp = accept(measure(5, "20", AlarmReading.Direction.ABOVE, "10", "20"), state);
        assertEquals(AlarmEvaluationResult.Transition.GRADE_UP, gradeUp.transition());
        assertEquals(AlarmGrade.CRITICAL, gradeUp.state().openAlarm().grade());
        var noDuplicate = accept(measure(6, "20", AlarmReading.Direction.ABOVE, "10", "20"), gradeUp.state());
        assertEquals(AlarmEvaluationResult.Transition.NONE, noDuplicate.transition());
    }

    @Test
    void threeNormalValuesRecoverWithoutClosingOpenAlarm() {
        var state = new AlarmEvaluationState(START, 3, 0, 0,
                new AlarmEvaluationState.OpenAlarm(AlarmGrade.CAUTION, false));
        for (int i = 1; i <= 2; i++) {
            var result = accept(measure(i, "9", AlarmReading.Direction.ABOVE, "10", "20"), state);
            assertEquals(AlarmEvaluationResult.Transition.NONE, result.transition());
            state = result.state();
        }
        var recovered = accept(measure(3, "9", AlarmReading.Direction.ABOVE, "10", "20"), state);
        assertEquals(AlarmEvaluationResult.Transition.RECOVER, recovered.transition());
        assertTrue(recovered.state().openAlarm().valueRecovered());
        assertEquals(AlarmGrade.CAUTION, recovered.state().openAlarm().grade());
        var stillOpen = accept(measure(4, "9", AlarmReading.Direction.ABOVE, "10", "20"), recovered.state());
        assertEquals(AlarmEvaluationResult.Transition.NONE, stillOpen.transition());
        assertTrue(stillOpen.state().openAlarm().valueRecovered());
    }

    @Test
    void recoveredOpenAlarmRecursWithoutCreatingAnotherAlarm() {
        var state = new AlarmEvaluationState(START, 0, 0, 3,
                new AlarmEvaluationState.OpenAlarm(AlarmGrade.CAUTION, true));
        state = accept(measure(1, "10", AlarmReading.Direction.ABOVE, "10", "20"), state).state();
        state = accept(measure(2, "10", AlarmReading.Direction.ABOVE, "10", "20"), state).state();
        var result = accept(measure(3, "10", AlarmReading.Direction.ABOVE, "10", "20"), state);
        assertEquals(AlarmEvaluationResult.Transition.RECUR, result.transition());
        assertFalse(result.state().openAlarm().valueRecovered());
    }

    @Test
    void configuredStateValueCountsBothStreaksAndUsesConfiguredGrade() {
        var state = AlarmEvaluationState.empty();
        state = accept(state(0, "0", 0, AlarmGrade.CAUTION), state).state();
        state = accept(state(1, "0", 0, AlarmGrade.CAUTION), state).state();
        var result = accept(state(2, "0", 0, AlarmGrade.CAUTION), state);
        assertEquals(AlarmEvaluationResult.Transition.OCCUR, result.transition());
        assertEquals(AlarmGrade.CAUTION, result.eventGrade());
        assertEquals(3, result.state().criticalStreak());
        var noGradeUp = accept(state(3, "0", 0, AlarmGrade.CAUTION), result.state());
        assertEquals(AlarmEvaluationResult.Transition.NONE, noGradeUp.transition());
    }

    @Test
    void belowDirectionAndUnconfiguredOrCumulativeSamples() {
        var result = accept(measure(0, "5", AlarmReading.Direction.BELOW, "10", "5"),
                AlarmEvaluationState.empty());
        assertEquals(1, result.state().criticalStreak());
        assertEquals(AlarmEvaluationResult.Transition.NONE, result.transition());
        assertFalse(service.evaluate(state(1, "1", null, null), result.state()).accepted());
        var cumulative = new AlarmReading(1, START.plusMinutes(2), AlarmReading.Kind.CUMULATIVE,
                BigDecimal.TEN, null, null, null, null, null);
        assertFalse(service.evaluate(cumulative, result.state()).accepted());
    }

    @Test
    void outOfOrderAndDuplicateSamplesDoNotChangeState() {
        var current = new AlarmEvaluationState(START.plusMinutes(2), 2, 1, 0, null);
        assertEquals(current, service.evaluate(measure(2, "20", AlarmReading.Direction.ABOVE,
                "10", "20"), current).state());
        assertEquals(current, service.evaluate(measure(1, "20", AlarmReading.Direction.ABOVE,
                "10", "20"), current).state());
    }

    @Test
    void configuredStreakCanDifferFromDefaultThree() {
        var first = service.evaluate(measure(0, "10", AlarmReading.Direction.ABOVE,
                "10", "20"), AlarmEvaluationState.empty(), 2);
        var second = service.evaluate(measure(1, "10", AlarmReading.Direction.ABOVE,
                "10", "20"), first.state(), 2);
        assertEquals(AlarmEvaluationResult.Transition.OCCUR, second.transition());
    }

    @Test
    void invalidLimitOrderAndIncompleteStateRuleAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(
                measure(0, "12", AlarmReading.Direction.ABOVE, "20", "10"),
                AlarmEvaluationState.empty()));
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(
                state(0, "1", 1, null), AlarmEvaluationState.empty()));
    }

    private AlarmEvaluationResult accept(AlarmReading reading, AlarmEvaluationState previous) {
        var result = service.evaluate(reading, previous);
        assertTrue(result.accepted());
        return result;
    }

    private AlarmReading measure(int minute, String value, AlarmReading.Direction direction,
            String caution, String critical) {
        return new AlarmReading(1, START.plusMinutes(minute), AlarmReading.Kind.MEASUREMENT,
                new BigDecimal(value), direction, new BigDecimal(caution), new BigDecimal(critical),
                null, null);
    }

    private AlarmReading state(int minute, String value, Integer alarmValue, AlarmGrade grade) {
        return new AlarmReading(1, START.plusMinutes(minute), AlarmReading.Kind.STATE,
                new BigDecimal(value), null, null, null, alarmValue, grade);
    }
}
