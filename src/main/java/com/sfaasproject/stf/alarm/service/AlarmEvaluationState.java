package com.sfaasproject.stf.alarm.service;

import java.time.LocalDateTime;

/** Snapshot mapped from measurement_latest and the one unfinished alarm, if any. */
public record AlarmEvaluationState(
        LocalDateTime lastMeasuredAt,
        int outStreak,
        int criticalStreak,
        int normalStreak,
        OpenAlarm openAlarm) {

    public AlarmEvaluationState {
        if (outStreak < 0 || criticalStreak < 0 || normalStreak < 0) {
            throw new IllegalArgumentException("Streak counts cannot be negative");
        }
    }

    public static AlarmEvaluationState empty() {
        return new AlarmEvaluationState(null, 0, 0, 0, null);
    }

    public record OpenAlarm(AlarmGrade grade, boolean valueRecovered) {
        public OpenAlarm {
            if (grade == null) throw new IllegalArgumentException("Open alarm grade is required");
        }
    }
}
