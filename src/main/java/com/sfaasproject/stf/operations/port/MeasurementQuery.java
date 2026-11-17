package com.sfaasproject.stf.operations.port;

import com.sfaasproject.stf.operations.dto.V7Contract.MeasurementPoint;

import java.time.OffsetDateTime;
import java.util.List;

public interface MeasurementQuery {
    List<MeasurementPoint> points(long itemId, OffsetDateTime from, OffsetDateTime to);

    List<V7Context.StatusInterval> statuses(OffsetDateTime from, OffsetDateTime to);

    void resetAlarmStreaks(long itemId);
}
