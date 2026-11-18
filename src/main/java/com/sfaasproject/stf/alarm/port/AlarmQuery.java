package com.sfaasproject.stf.alarm.port;

import com.sfaasproject.stf.alarm.service.AlarmGrade;

import java.time.OffsetDateTime;
import java.util.List;

/** B-owned read port for A equipment state, deletion guards and measurement markers. */
public interface AlarmQuery {
    record Reference(long alarmId, long equipmentId, long dataItemId) {}

    record Marker(
            long alarmId,
            long dataItemId,
            String grade,
            String processStatus,
            OffsetDateTime occurredAt,
            OffsetDateTime closedAt) {}

    Reference find(long alarmId);

    long activeCount(long equipmentId);

    boolean abnormal(long equipmentId, AlarmGrade grade);

    List<Marker> markers(long equipmentId, OffsetDateTime from, OffsetDateTime to);
}
