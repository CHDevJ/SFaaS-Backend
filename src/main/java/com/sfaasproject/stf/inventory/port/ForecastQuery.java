package com.sfaasproject.stf.inventory.port;

import com.sfaasproject.stf.operations.dto.V7Contract.AssemblyStopForecastResponse;

/** B-owned forecast boundary for other domains. No repository or entity crosses this port. */
public interface ForecastQuery {
    AssemblyStopForecastResponse overview();
}
