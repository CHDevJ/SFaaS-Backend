package com.sfaasproject.stf.equipment.dto;

import com.sfaasproject.stf.equipment.service.MasterDataItemService.Command;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * MST-013 요청 (openapi ThresholdWriteRequest = MeasurementThreshold 또는 StateAlarmCondition).
 * 두 모양의 칸을 모두 받아 두고, 어느 모양인지와 항목의 값 종류가 맞는지는 서비스가 확인한다.
 */
public record ThresholdWriteRequest(
        Double cautionValue,
        Double criticalValue,
        @Pattern(regexp = "ABOVE|BELOW") String direction,
        @Min(0) @Max(1) Integer alarmValue,
        @Pattern(regexp = "CAUTION|CRITICAL") String grade) {

    public boolean hasMeasurementFields() {
        return cautionValue != null || criticalValue != null || direction != null;
    }

    public boolean hasStateFields() {
        return alarmValue != null || grade != null;
    }

    public Command.Threshold threshold() {
        return new Command.Threshold(cautionValue, criticalValue, direction);
    }

    public Command.StateAlarm stateAlarm() {
        return new Command.StateAlarm(alarmValue, grade);
    }
}
