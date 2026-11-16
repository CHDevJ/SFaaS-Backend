package com.sfaasproject.stf.alarm.service;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Deterministic alarm rules for one data item; persistence is owned by the ingestion transaction. */
@Service
public class AlarmDecisionService {
    public AlarmEvaluationResult evaluate(AlarmReading reading, AlarmEvaluationState previous) {
        return evaluate(reading, previous, 3);
    }

    public AlarmEvaluationResult evaluate(AlarmReading reading, AlarmEvaluationState previous,
            int requiredStreak) {
        if (requiredStreak <= 0) throw new IllegalArgumentException("Required streak must be positive");
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(previous, "previous");
        validate(reading);

        if (reading.kind() == AlarmReading.Kind.CUMULATIVE || !hasAlarmRule(reading)
                || previous.lastMeasuredAt() != null
                && !reading.measuredAt().isAfter(previous.lastMeasuredAt())) {
            return AlarmEvaluationResult.ignored(previous);
        }

        AlarmGrade valueGrade = classify(reading);
        boolean out = valueGrade != null;
        // A configured STATE alarm counts in both out and critical streaks, even when its grade is CAUTION.
        boolean criticalStreakValue = out && (reading.kind() == AlarmReading.Kind.STATE
                || valueGrade == AlarmGrade.CRITICAL);
        int outStreak = out ? Math.addExact(previous.outStreak(), 1) : 0;
        int criticalStreak = criticalStreakValue ? Math.addExact(previous.criticalStreak(), 1) : 0;
        int normalStreak = out ? 0 : Math.addExact(previous.normalStreak(), 1);
        AlarmEvaluationState.OpenAlarm open = previous.openAlarm();
        AlarmEvaluationResult.Transition transition = AlarmEvaluationResult.Transition.NONE;
        AlarmGrade eventGrade = null;
        BigDecimal matchedLimit = null;

        if (open == null && outStreak == requiredStreak) {
            eventGrade = occurrenceGrade(reading, criticalStreak, requiredStreak);
            matchedLimit = matchedLimit(reading, eventGrade);
            open = new AlarmEvaluationState.OpenAlarm(eventGrade, false);
            transition = AlarmEvaluationResult.Transition.OCCUR;
        } else if (open != null && open.valueRecovered() && outStreak == requiredStreak) {
            eventGrade = open.grade() == AlarmGrade.CRITICAL ? AlarmGrade.CRITICAL
                    : occurrenceGrade(reading, criticalStreak, requiredStreak);
            matchedLimit = matchedLimit(reading, eventGrade);
            open = new AlarmEvaluationState.OpenAlarm(eventGrade, false);
            transition = AlarmEvaluationResult.Transition.RECUR;
        } else if (open != null && !open.valueRecovered() && normalStreak == requiredStreak) {
            eventGrade = open.grade();
            open = new AlarmEvaluationState.OpenAlarm(open.grade(), true);
            transition = AlarmEvaluationResult.Transition.RECOVER;
        } else if (open != null && !open.valueRecovered()
                && open.grade() == AlarmGrade.CAUTION
                && reading.kind() == AlarmReading.Kind.MEASUREMENT
                && criticalStreak == requiredStreak) {
            eventGrade = AlarmGrade.CRITICAL;
            matchedLimit = reading.criticalLimit();
            open = new AlarmEvaluationState.OpenAlarm(AlarmGrade.CRITICAL, false);
            transition = AlarmEvaluationResult.Transition.GRADE_UP;
        }

        var next = new AlarmEvaluationState(reading.measuredAt(), outStreak, criticalStreak,
                normalStreak, open);
        return new AlarmEvaluationResult(next, transition, eventGrade, matchedLimit, valueGrade, true);
    }

    private boolean hasAlarmRule(AlarmReading reading) {
        return reading.kind() == AlarmReading.Kind.MEASUREMENT
                ? reading.cautionLimit() != null
                : reading.alarmStateValue() != null;
    }

    private AlarmGrade classify(AlarmReading reading) {
        if (reading.kind() == AlarmReading.Kind.STATE) {
            return reading.value().compareTo(BigDecimal.valueOf(reading.alarmStateValue())) == 0
                    ? reading.alarmStateGrade() : null;
        }
        int criticalComparison = reading.value().compareTo(reading.criticalLimit());
        int cautionComparison = reading.value().compareTo(reading.cautionLimit());
        if (reading.direction() == AlarmReading.Direction.ABOVE) {
            if (criticalComparison >= 0) return AlarmGrade.CRITICAL;
            if (cautionComparison >= 0) return AlarmGrade.CAUTION;
        } else {
            if (criticalComparison <= 0) return AlarmGrade.CRITICAL;
            if (cautionComparison <= 0) return AlarmGrade.CAUTION;
        }
        return null;
    }

    private AlarmGrade occurrenceGrade(AlarmReading reading, int criticalStreak, int requiredStreak) {
        if (reading.kind() == AlarmReading.Kind.STATE) return reading.alarmStateGrade();
        return criticalStreak >= requiredStreak ? AlarmGrade.CRITICAL : AlarmGrade.CAUTION;
    }

    private BigDecimal matchedLimit(AlarmReading reading, AlarmGrade grade) {
        if (reading.kind() == AlarmReading.Kind.STATE) {
            return BigDecimal.valueOf(reading.alarmStateValue());
        }
        return grade == AlarmGrade.CRITICAL ? reading.criticalLimit() : reading.cautionLimit();
    }

    private void validate(AlarmReading reading) {
        if (reading.dataItemId() <= 0 || reading.measuredAt() == null || reading.kind() == null
                || reading.value() == null) {
            throw new IllegalArgumentException("Data item, timestamp, kind and value are required");
        }
        if (reading.kind() == AlarmReading.Kind.MEASUREMENT) {
            boolean noLimits = reading.cautionLimit() == null && reading.criticalLimit() == null
                    && reading.direction() == null;
            if (noLimits) return;
            if (reading.cautionLimit() == null || reading.criticalLimit() == null
                    || reading.direction() == null) {
                throw new IllegalArgumentException("Measurement alarm limits and direction must be configured together");
            }
            int order = reading.cautionLimit().compareTo(reading.criticalLimit());
            if (reading.direction() == AlarmReading.Direction.ABOVE && order >= 0
                    || reading.direction() == AlarmReading.Direction.BELOW && order <= 0) {
                throw new IllegalArgumentException("Caution and critical limits are out of order");
            }
        } else if (reading.kind() == AlarmReading.Kind.STATE) {
            if (reading.value().compareTo(BigDecimal.ZERO) != 0
                    && reading.value().compareTo(BigDecimal.ONE) != 0) {
                throw new IllegalArgumentException("State value must be zero or one");
            }
            if (reading.alarmStateValue() == null && reading.alarmStateGrade() == null) return;
            if (reading.alarmStateValue() == null || reading.alarmStateGrade() == null
                    || reading.alarmStateValue() != 0 && reading.alarmStateValue() != 1) {
                throw new IllegalArgumentException("State alarm value and grade must be configured together");
            }
        }
    }
}
