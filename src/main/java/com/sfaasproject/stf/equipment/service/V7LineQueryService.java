package com.sfaasproject.stf.equipment.service;

import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.operations.port.V7Context;

public class V7LineQueryService {
    private final V7Context context;

    public V7LineQueryService(V7Context context) {
        this.context = context;
    }

    public LineListResponse lines() {
        return new LineListResponse(
                true,
                context.lines().stream()
                        .filter(l -> !l.deleted())
                        .map(l -> new LineSummary(l.id(), l.name(), l.displayOrder(), l.type()))
                        .toList());
    }
}
