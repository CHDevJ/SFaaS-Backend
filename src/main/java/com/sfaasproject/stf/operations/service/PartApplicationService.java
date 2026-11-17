package com.sfaasproject.stf.operations.service;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BPorts.*;

public class PartApplicationService {
    private final Parts parts;
    private final Users users;
    private final UnitOfWork transactions;

    public PartApplicationService(Parts parts, Users users, UnitOfWork transactions) {
        this.parts = parts;
        this.users = users;
        this.transactions = transactions;
    }

    public PartReplacementResponse createPartReplacement(
            Long equipmentId, Long partId, PartReplacementRequest request) {
        var actor = BAccess.current();
        Checks.required(equipmentId, "equipmentId");
        Checks.positive(equipmentId, "equipmentId");
        Checks.required(partId, "partId");
        Checks.positive(partId, "partId");
        return transactions.execute(
                () ->
                        new PartReplacementResponse(
                                true, replace(equipmentId, partId, request, actor.userId(), null)));
    }

    public PartReplacementRecord replace(
            long equipmentId,
            long partId,
            PartReplacementRequest request,
            long actorId,
            Long actionId) {
        var part = parts.lock(partId);
        if (part == null || part.equipmentId() != equipmentId)
            throw Checks.missing("PART_NOT_FOUND");
        var user = users.active(actorId);
        if (user == null) throw Checks.missing("USER_NOT_FOUND");
        return parts.replace(part, request, user, actionId);
    }
}
