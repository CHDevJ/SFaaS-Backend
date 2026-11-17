package com.sfaasproject.stf.operations.adapter;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BPorts.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class JdbcBInventory implements InventoryRuntime {
    private final JdbcBDatabase db;

    public JdbcBInventory(JdbcBDatabase db) {
        this.db = db;
    }

    @Override
    public void applyProduction(String eventId, long equipmentId, int quantity, OffsetDateTime at) {
        var r =
                db.one(
                        "SELECT p.line_id,p.process_seq,l.line_type FROM equipment e JOIN process p"
                            + " ON p.process_id=e.process_id JOIN line l ON l.line_id=p.line_id"
                            + " WHERE e.equipment_id=? AND e.deleted_at IS NULL AND l.deleted_at IS"
                            + " NULL",
                        equipmentId);
        if (r == null) return;
        long line = number(r, "line_id");
        var max = db.one("SELECT MAX(process_seq) AS last_seq FROM process WHERE line_id=?", line);
        if (number(r, "process_seq") != number(max, "last_seq")) return;
        if ("ASSEMBLY".equals(string(r, "line_type"))) {
            for (var part :
                    db.rows(
                            "SELECT line_id FROM line WHERE line_type='PART' AND deleted_at IS NULL"
                                    + " ORDER BY line_id"))
                adjust(number(part, "line_id"), -quantity);
        } else adjust(line, quantity);
    }

    private void adjust(long line, int delta) {
        db.rows("SELECT line_id FROM line WHERE line_id=? FOR UPDATE", line);
        var previous =
                db.one(
                        "SELECT qty FROM stock_history WHERE line_id=? ORDER BY recorded_at DESC"
                                + " LIMIT 1",
                        line);
        long next =
                Math.max(0, Math.addExact(previous == null ? 0 : number(previous, "qty"), delta));
        var at = time(db.now().withNano(0));
        int changed =
                db.update(
                        "UPDATE stock_history SET qty=? WHERE line_id=? AND recorded_at=?",
                        next,
                        line,
                        at);
        if (changed == 0)
            db.update(
                    "INSERT INTO stock_history(line_id,recorded_at,qty) VALUES (?,?,?)",
                    line,
                    at,
                    next);
    }

    @Override
    public List<InventoryLine> recalculate(OffsetDateTime at) {
        var result = new ArrayList<InventoryLine>();
        for (var line :
                db.rows(
                        "SELECT line_id,name FROM line WHERE line_type='PART' AND deleted_at IS"
                                + " NULL ORDER BY sort_order,line_id")) {
            long id = number(line, "line_id");
            var stock =
                    db.one(
                            "SELECT qty FROM stock_history WHERE line_id=? ORDER BY recorded_at"
                                    + " DESC LIMIT 1",
                            id);
            BigDecimal produced = rate(id, at, false), consumed = rate(id, at, true);
            result.add(
                    new InventoryLine(
                            id,
                            string(line, "name"),
                            stock == null ? 0 : number(stock, "qty"),
                            consumed,
                            produced,
                            at));
        }
        return result;
    }

    private BigDecimal rate(long line, OffsetDateTime at, boolean assembly) {
        String filter = assembly ? "l.line_type='ASSEMBLY'" : "l.line_id=?";
        String sql =
                "SELECT COALESCE(SUM(pl.qty),0) AS qty FROM production_log pl JOIN equipment e ON"
                    + " e.equipment_id=pl.equipment_id JOIN process p ON p.process_id=e.process_id"
                    + " JOIN line l ON l.line_id=p.line_id WHERE "
                        + filter
                        + " AND e.deleted_at IS NULL AND p.process_seq=(SELECT MAX(p2.process_seq)"
                        + " FROM process p2 WHERE p2.line_id=p.line_id) AND pl.minute_at>=? AND"
                        + " pl.minute_at<?";
        var row =
                assembly
                        ? db.one(sql, time(at.minusMinutes(10)), time(at))
                        : db.one(sql, line, time(at.minusMinutes(10)), time(at));
        return BigDecimal.valueOf(number(row, "qty"))
                .divide(BigDecimal.TEN, 4, java.math.RoundingMode.HALF_UP);
    }
}
