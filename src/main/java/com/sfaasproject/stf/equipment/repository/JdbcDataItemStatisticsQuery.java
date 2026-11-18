package com.sfaasproject.stf.equipment.repository;

import com.sfaasproject.stf.equipment.service.DataItemStatisticsQuery;
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

/** measurement_latest, measurement, alarm 을 JDBC 로 읽는다. */
@Repository
public class JdbcDataItemStatisticsQuery implements DataItemStatisticsQuery {

    private final JdbcTemplate jdbc;

    public JdbcDataItemStatisticsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, Latest> latest(Collection<Long> dataItemIds) {
        Map<Long, Latest> result = new HashMap<>();
        if (dataItemIds.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT data_item_id, measured_value, measured_at, received_at FROM measurement_latest"
                        + " WHERE data_item_id IN ("
                        + placeholders(dataItemIds) + ")",
                rs -> {
                    Timestamp measured = rs.getTimestamp("measured_at");
                    Timestamp received = rs.getTimestamp("received_at");
                    result.put(rs.getLong("data_item_id"), new Latest(rs.getDouble("measured_value"),
                            measured == null ? null : measured.toLocalDateTime(),
                            received == null ? null : received.toLocalDateTime()));
                }, dataItemIds.toArray());
        return result;
    }

    @Override
    public Map<Long, Range> ranges(Collection<Long> dataItemIds, LocalDateTime from) {
        Map<Long, Range> result = new HashMap<>();
        if (dataItemIds.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT data_item_id, MIN(measured_value) AS mn, AVG(measured_value) AS av,"
                        + " MAX(measured_value) AS mx FROM measurement WHERE measured_at >= ? AND data_item_id IN ("
                        + placeholders(dataItemIds) + ") GROUP BY data_item_id",
                rs -> {
                    result.put(rs.getLong("data_item_id"),
                            new Range(rs.getDouble("mn"), rs.getDouble("av"), rs.getDouble("mx")));
                }, args(from, dataItemIds));
        return result;
    }

    @Override
    public Map<Long, Integer> alarmCounts(Collection<Long> dataItemIds, LocalDateTime from) {
        Map<Long, Integer> result = new HashMap<>();
        if (dataItemIds.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT data_item_id, COUNT(*) AS cnt FROM alarm WHERE occurred_at >= ? AND data_item_id IN ("
                        + placeholders(dataItemIds) + ") GROUP BY data_item_id",
                rs -> {
                    result.put(rs.getLong("data_item_id"), rs.getInt("cnt"));
                }, args(from, dataItemIds));
        return result;
    }

    private static String placeholders(Collection<Long> ids) {
        return String.join(",", Collections.nCopies(ids.size(), "?"));
    }

    private static Object[] args(LocalDateTime from, Collection<Long> ids) {
        List<Object> args = new ArrayList<>(ids.size() + 1);
        args.add(Timestamp.valueOf(from));
        args.addAll(ids);
        return args.toArray();
    }
}
