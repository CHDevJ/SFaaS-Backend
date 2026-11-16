package com.sfaasproject.stf.system.service;

import com.sfaasproject.stf.alarm.service.AlarmGrade;
import com.sfaasproject.stf.common.DomainException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** MST-013 threshold validation before a data-item definition is stored. */
@Service
public class DataItemThresholdValidationService {
    public enum ValueType { MEASUREMENT, STATE, CUMULATIVE }
    public enum Direction { ABOVE, BELOW }
    public record Measurement(BigDecimal cautionValue, BigDecimal criticalValue,
            Direction direction) { }
    public record State(int alarmValue, AlarmGrade grade) { }

    public void validate(ValueType type, Measurement measurement, State state) {
        if (type == null) throw invalidType();
        switch (type) {
            case MEASUREMENT -> {
                if (state != null) throw invalidType();
                if (measurement != null) validateMeasurement(measurement);
            }
            case STATE -> {
                if (measurement != null) throw invalidType();
                if (state != null) validateState(state);
            }
            case CUMULATIVE -> {
                if (measurement != null || state != null) throw invalidType();
            }
        }
    }

    private void validateMeasurement(Measurement threshold) {
        if (threshold.cautionValue() == null || threshold.criticalValue() == null
                || threshold.direction() == null) throw invalidType();
        int order = threshold.cautionValue().compareTo(threshold.criticalValue());
        if (threshold.direction() == Direction.ABOVE && order >= 0
                || threshold.direction() == Direction.BELOW && order <= 0) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "INVALID_THRESHOLD_ORDER", "주의·경보 기준 순서를 확인해 주세요.");
        }
    }

    private void validateState(State state) {
        if (state.grade() == null || state.alarmValue() != 0 && state.alarmValue() != 1) {
            throw invalidType();
        }
    }

    private DomainException invalidType() {
        return new DomainException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR",
                "데이터 항목의 값 종류와 알람 기준을 확인해 주세요.");
    }
}
