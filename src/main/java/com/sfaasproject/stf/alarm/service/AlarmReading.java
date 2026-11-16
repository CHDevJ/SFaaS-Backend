package com.sfaasproject.stf.alarm.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One accepted data-item sample and the alarm configuration effective for it. */
public record AlarmReading(
        long dataItemId,
        LocalDateTime measuredAt,
        Kind kind,
        BigDecimal value,
        Direction direction,
        BigDecimal cautionLimit,
        BigDecimal criticalLimit,
        Integer alarmStateValue,
        AlarmGrade alarmStateGrade) {

    public enum Kind { MEASUREMENT, STATE, CUMULATIVE }
    public enum Direction { ABOVE, BELOW }
}
