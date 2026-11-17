package com.sfaasproject.stf.equipment.repository;

import com.sfaasproject.stf.equipment.service.EquipmentRuntimeQuery;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * equipment_state 와 alarm 을 JDBC 로 읽는다. 발생 중 알람은 종합현황(OVW-001)과 같이
 * 처리 상태가 CLOSED 가 아닌 알람으로 센다.
 */
@Repository
public class JdbcEquipmentRuntimeQuery implements EquipmentRuntimeQuery {

    private final JdbcTemplate jdbc;

    public JdbcEquipmentRuntimeQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, Runtime> find(Collection<Long> equipmentIds) {
        if (equipmentIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(equipmentIds);
        String in = String.join(",", Collections.nCopies(ids.size(), "?"));
        Object[] args = ids.toArray();

        Map<Long, String> statuses = new HashMap<>();
        Map<Long, LocalDateTime> since = new HashMap<>();
        jdbc.query("SELECT equipment_id, status, status_since FROM equipment_state WHERE equipment_id IN (" + in + ")",
                rs -> {
                    long id = rs.getLong("equipment_id");
                    statuses.put(id, rs.getString("status"));
                    Timestamp started = rs.getTimestamp("status_since");
                    since.put(id, started == null ? null : started.toLocalDateTime());
                }, args);

        Map<Long, Integer> alarms = new HashMap<>();
        jdbc.query("SELECT equipment_id, COUNT(*) AS cnt FROM alarm WHERE process_state <> 'CLOSED'"
                        + " AND equipment_id IN (" + in + ") GROUP BY equipment_id",
                rs -> {
                    alarms.put(rs.getLong("equipment_id"), rs.getInt("cnt"));
                }, args);

        Map<Long, Runtime> result = new HashMap<>();
        for (Long id : ids) {
            if (statuses.containsKey(id) || alarms.containsKey(id)) {
                result.put(id, new Runtime(statuses.get(id), since.get(id), alarms.getOrDefault(id, 0)));
            }
        }
        return result;
    }
}
