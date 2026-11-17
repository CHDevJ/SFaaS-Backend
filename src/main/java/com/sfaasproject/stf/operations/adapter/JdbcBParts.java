package com.sfaasproject.stf.operations.adapter;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BPorts.*;

public class JdbcBParts implements Parts {
    private final JdbcBDatabase db;

    public JdbcBParts(JdbcBDatabase db) {
        this.db = db;
    }

    @Override
    public Part lock(long id) {
        var r =
                db.one(
                        "SELECT p.part_id,p.equipment_id,p.name FROM part p JOIN equipment e ON"
                            + " e.equipment_id=p.equipment_id WHERE p.part_id=? AND p.deleted_at IS"
                            + " NULL AND e.deleted_at IS NULL FOR UPDATE",
                        id);
        return r == null
                ? null
                : new Part(number(r, "part_id"), number(r, "equipment_id"), string(r, "name"));
    }

    @Override
    public PartReplacementRecord replace(
            Part part, PartReplacementRequest request, UserBrief worker, Long actionId) {
        long id =
                db.insert(
                        "INSERT INTO"
                            + " part_replacement(part_id,replaced_at,reason,work_content,worker_id,action_id)"
                            + " VALUES (?,?,?,?,?,?)",
                        part.id(),
                        time(request.replacedAt()),
                        request.reason(),
                        request.workContent(),
                        worker.userId(),
                        actionId);
        db.update(
                "UPDATE part SET last_replaced_at=?,updated_at=? WHERE part_id=?",
                time(request.replacedAt()),
                time(db.now()),
                part.id());
        return new PartReplacementRecord(
                id,
                request.replacedAt(),
                part.id(),
                part.name(),
                request.reason(),
                request.workContent(),
                worker);
    }
}
