package com.sfaasproject.stf.operations.port;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A's validated production-window projection, including reset/inconsistent/missing quality. */
public interface CounterSnapshot {
    BigDecimal productionRate(long lineId, OffsetDateTime at, int windowMinutes);

    String counterQuality(long lineId, OffsetDateTime at, int windowMinutes);
}
