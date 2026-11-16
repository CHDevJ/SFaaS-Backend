package com.sfaasproject.stf.measurement.repository;

import com.sfaasproject.stf.alarm.service.AlarmEvaluationResult;
import com.sfaasproject.stf.alarm.service.AlarmGrade;
import com.sfaasproject.stf.equipment.service.EquipmentStatusDecisionService.Status;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** DB operations used by one transactional equipment message. */
public interface IngestionStore {
    record Equipment(long id, String code) { }
    record DataItem(long id, long equipmentId, String code, String name, String kind,
            String role, String direction, BigDecimal cautionLimit, BigDecimal criticalLimit,
            Integer alarmStateValue, AlarmGrade alarmStateGrade, LocalTime resetTime) { }
    record Latest(BigDecimal value, LocalDateTime measuredAt, int outStreak,
            int criticalStreak, int normalStreak) { }
    record OpenAlarm(long id, AlarmGrade grade, boolean valueRecovered) { }

    Equipment findActiveEquipment(String code);
    List<DataItem> findActiveItems(long equipmentId);
    Latest findLatestForUpdate(long dataItemId);
    OpenAlarm findOpenAlarmForUpdate(long dataItemId);
    boolean measurementExists(long dataItemId, LocalDateTime measuredAt);
    void insertMeasurement(long dataItemId, LocalDateTime measuredAt,
            BigDecimal value, LocalDateTime receivedAt);
    void saveLatest(long dataItemId, BigDecimal value, LocalDateTime measuredAt,
            LocalDateTime receivedAt, String valueGrade, int outStreak,
            int criticalStreak, int normalStreak);
    void applyAlarm(Equipment equipment, DataItem item, BigDecimal value,
            LocalDateTime measuredAt, OpenAlarm open, AlarmEvaluationResult result);
    void addProduction(long equipmentId, LocalDateTime minuteAt, int quantity);
    Boolean findRunSignal(long equipmentId);
    boolean hasAbnormalOpenAlarmGrade(long equipmentId, AlarmGrade grade);
    void saveEquipmentStatus(long equipmentId, Status status,
            LocalDateTime statusAt, LocalDateTime receivedAt);
}
