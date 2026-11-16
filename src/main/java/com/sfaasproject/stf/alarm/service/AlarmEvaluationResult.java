package com.sfaasproject.stf.alarm.service;

import java.math.BigDecimal;

/** The caller persists state and applies any transition in the same transaction. */
public record AlarmEvaluationResult(
        AlarmEvaluationState state,
        Transition transition,
        AlarmGrade eventGrade,
        BigDecimal matchedLimit,
        AlarmGrade valueGrade,
        boolean accepted) {

    public enum Transition { NONE, OCCUR, GRADE_UP, RECOVER, RECUR }

    public static AlarmEvaluationResult ignored(AlarmEvaluationState state) {
        return new AlarmEvaluationResult(state, Transition.NONE, null, null, null, false);
    }
}
