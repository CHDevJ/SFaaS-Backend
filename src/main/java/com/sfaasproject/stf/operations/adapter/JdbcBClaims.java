package com.sfaasproject.stf.operations.adapter;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.operations.port.BPorts.EventClaims;

import org.springframework.dao.DuplicateKeyException;

public class JdbcBClaims implements EventClaims {
    private final JdbcBDatabase db;

    public JdbcBClaims(JdbcBDatabase db) {
        this.db = db;
    }

    @Override
    public boolean claim(String id) {
        db.update(
                "DELETE FROM backend_event_claim WHERE event_id=? AND expires_at<?",
                id,
                time(db.now()));
        try {
            db.update(
                    "INSERT INTO backend_event_claim(event_id,expires_at) VALUES (?,?)",
                    id,
                    time(db.now().plusDays(1)));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }
}
