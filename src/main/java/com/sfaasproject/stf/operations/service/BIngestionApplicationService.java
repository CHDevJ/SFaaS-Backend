package com.sfaasproject.stf.operations.service;

import com.sfaasproject.stf.measurement.dto.*;
import com.sfaasproject.stf.measurement.service.IngestionProcessor;
import com.sfaasproject.stf.operations.port.BPorts.UnitOfWork;

import java.time.*;

public class BIngestionApplicationService {
    private final IngestionProcessor processor;
    private final UnitOfWork tx;
    private final Clock clock;

    public BIngestionApplicationService(IngestionProcessor processor, UnitOfWork tx, Clock clock) {
        this.processor = processor;
        this.tx = tx;
        this.clock = clock;
    }

    public IngestionAccepted accept(MeasurementIngestionRequest request) {
        return tx.execute(() -> processor.process(request, OffsetDateTime.now(clock)));
    }
}
