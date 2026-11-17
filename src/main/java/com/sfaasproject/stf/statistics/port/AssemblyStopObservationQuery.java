package com.sfaasproject.stf.statistics.port;

import java.time.OffsetDateTime;
import java.util.List;

/** B's observation boundary; A supplies catalog/state through its existing query port. */
public interface AssemblyStopObservationQuery {
    record Cause(Long shortageLineId, Long equipmentId, Long alarmId, OffsetDateTime evidenceAt) {}

    record Observation(
            OffsetDateTime at,
            List<Cause> causes,
            boolean recoveryConfirmed,
            Integer hourlyOutput,
            Long unitPrice) {}

    Observation observe();
}
