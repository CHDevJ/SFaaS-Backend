package com.sfaasproject.stf.equipment.repository;

import com.sfaasproject.stf.equipment.service.CommunicationQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** equipment·process·line·equipment_state·equipment_status_history 를 JDBC 로 읽는다. */
@Repository
public class JdbcCommunicationQuery implements CommunicationQuery {

    private final JdbcTemplate jdbc;

    public JdbcCommunicationQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Row> equipment(Long lineId, Long processId) {
        String sql = "SELECT e.equipment_id, e.equip_code, e.name, l.name AS line_name, e.comm_type, s.status,"
                + " s.status_since, s.last_received_at, e.created_at FROM equipment e"
                + " JOIN process p ON p.process_id = e.process_id JOIN line l ON l.line_id = p.line_id"
                + " LEFT JOIN equipment_state s ON s.equipment_id = e.equipment_id"
                + " WHERE e.deleted_at IS NULL AND l.deleted_at IS NULL"
                + (lineId == null ? "" : " AND l.line_id = ?")
                + (processId == null ? "" : " AND p.process_id = ?") + " ORDER BY e.equipment_id";
        List<Object> args = new ArrayList<>();
        if (lineId != null) {
            args.add(lineId);
        }
        if (processId != null) {
            args.add(processId);
        }
        return jdbc.query(sql, (rs, row) -> row(rs), args.toArray());
    }

    @Override
    public Map<Long, List<Interval>> disconnections(Collection<Long> ids, LocalDateTime from, LocalDateTime to) {
        Map<Long, List<Interval>> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<Object> args = new ArrayList<>(ids);
        args.add(Timestamp.valueOf(to));
        args.add(Timestamp.valueOf(from));
        jdbc.query("SELECT equipment_id, started_at, ended_at FROM equipment_status_history"
                        + " WHERE equipment_id IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")"
                        + " AND status = 'COMMUNICATION_LOST' AND started_at <= ? AND (ended_at IS NULL OR ended_at >= ?)"
                        + " ORDER BY equipment_id, started_at",
                rs -> {
                    result.computeIfAbsent(rs.getLong("equipment_id"), id -> new ArrayList<>()).add(new Interval(
                            local(rs.getTimestamp("started_at")), local(rs.getTimestamp("ended_at"))));
                }, args.toArray());
        return result;
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getLong("equipment_id"), rs.getString("equip_code"), rs.getString("name"),
                rs.getString("line_name"), rs.getString("comm_type"), rs.getString("status"),
                local(rs.getTimestamp("status_since")), local(rs.getTimestamp("last_received_at")),
                local(rs.getTimestamp("created_at")));
    }

    private static LocalDateTime local(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
