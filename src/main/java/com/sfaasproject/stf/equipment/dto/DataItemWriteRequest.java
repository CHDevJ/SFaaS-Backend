package com.sfaasproject.stf.equipment.dto;

import com.sfaasproject.stf.equipment.service.MasterDataItemService.Command;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * MST-011, MST-012 요청 (openapi DataItemWriteRequest). valueType 에 맞지 않는 칸은 서비스에서 400 으로 거른다.
 *
 * <p>dataItemCode 는 수집(INT-001)의 dataItemCode 와 맞추는 값으로, v7 에 없던 것을 추가했다(2026-11-18 사용자 결정).
 * 등록(MST-011)에는 꼭 있어야 하고, 수정(MST-012)에서는 바꿀 수 없으므로 보내도 무시한다.
 * 길이는 ERD data_item 컬럼에 맞춘다(이름 50, 단위 20, 0/1 표시 20).
 *
 * <p>MST-012 에서 두 기준 칸은 "보내지 않음"(기존 값 유지)과 "null 을 보냄"(지움)을 구분해야 해서
 * record 대신 setter 로 받고, setter 가 불렸는지(JSON 에 칸이 있었는지)를 기억한다.
 */
public class DataItemWriteRequest {

    @Size(min = 1, max = 50) @Pattern(regexp = "^[a-z][a-z0-9_]*$")
    private String dataItemCode;

    @NotBlank @Size(max = 50)
    private String dataItemName;

    @NotNull @Pattern(regexp = "MEASUREMENT|STATE|CUMULATIVE")
    private String valueType;

    @Size(max = 20)
    private String unit;

    @NotNull @Min(1)
    private Integer collectionIntervalSeconds;

    @Min(0) @Max(10)
    private Integer decimalPlaces;

    @Size(max = 20)
    private String zeroDisplayName;

    @Size(max = 20)
    private String oneDisplayName;

    @Valid
    private MeasurementThresholdBody measurementThreshold;
    private boolean measurementThresholdPresent;

    @Valid
    private StateAlarmConditionBody stateAlarmCondition;
    private boolean stateAlarmConditionPresent;

    /** openapi MeasurementThreshold. */
    public record MeasurementThresholdBody(
            @NotNull Double cautionValue,
            @NotNull Double criticalValue,
            @NotNull @Pattern(regexp = "ABOVE|BELOW") String direction) { }

    /** openapi StateAlarmCondition. */
    public record StateAlarmConditionBody(
            @NotNull @Min(0) @Max(1) Integer alarmValue,
            @NotNull @Pattern(regexp = "CAUTION|CRITICAL") String grade) { }

    public Command toCommand() {
        return new Command(dataItemCode, dataItemName, valueType, unit, collectionIntervalSeconds, decimalPlaces,
                zeroDisplayName, oneDisplayName,
                measurementThreshold == null ? null : new Command.Threshold(measurementThreshold.cautionValue(),
                        measurementThreshold.criticalValue(), measurementThreshold.direction()),
                stateAlarmCondition == null ? null : new Command.StateAlarm(stateAlarmCondition.alarmValue(),
                        stateAlarmCondition.grade()),
                measurementThresholdPresent, stateAlarmConditionPresent);
    }

    public String getDataItemCode() {
        return dataItemCode;
    }

    public void setDataItemCode(String dataItemCode) {
        this.dataItemCode = dataItemCode;
    }

    public String getDataItemName() {
        return dataItemName;
    }

    public void setDataItemName(String dataItemName) {
        this.dataItemName = dataItemName;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Integer getCollectionIntervalSeconds() {
        return collectionIntervalSeconds;
    }

    public void setCollectionIntervalSeconds(Integer collectionIntervalSeconds) {
        this.collectionIntervalSeconds = collectionIntervalSeconds;
    }

    public Integer getDecimalPlaces() {
        return decimalPlaces;
    }

    public void setDecimalPlaces(Integer decimalPlaces) {
        this.decimalPlaces = decimalPlaces;
    }

    public String getZeroDisplayName() {
        return zeroDisplayName;
    }

    public void setZeroDisplayName(String zeroDisplayName) {
        this.zeroDisplayName = zeroDisplayName;
    }

    public String getOneDisplayName() {
        return oneDisplayName;
    }

    public void setOneDisplayName(String oneDisplayName) {
        this.oneDisplayName = oneDisplayName;
    }

    public MeasurementThresholdBody getMeasurementThreshold() {
        return measurementThreshold;
    }

    public void setMeasurementThreshold(MeasurementThresholdBody measurementThreshold) {
        this.measurementThreshold = measurementThreshold;
        this.measurementThresholdPresent = true;
    }

    public StateAlarmConditionBody getStateAlarmCondition() {
        return stateAlarmCondition;
    }

    public void setStateAlarmCondition(StateAlarmConditionBody stateAlarmCondition) {
        this.stateAlarmCondition = stateAlarmCondition;
        this.stateAlarmConditionPresent = true;
    }
}
