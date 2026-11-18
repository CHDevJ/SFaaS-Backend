package com.sfaasproject.stf.measurement.repository;

import com.sfaasproject.stf.measurement.service.MeasurementTrendQuery;
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

/**
 * measurement, equipment_status_history, equipment_state, alarm 을 JDBC 로 읽는다.
 * 집계 칸 번호는 FLOOR(TIMESTAMPDIFF(SECOND, anchor, measured_at) / bucket) 이고 MySQL·H2 에서 같게 동작한다.
 */
@Repository
public class JdbcMeasurementTrendQuery implements MeasurementTrendQuery {

    private static final java.time.format.DateTimeFormatter ANCHOR =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public JdbcMeasurementTrendQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, Integer> counts(Collection<Long> ids, LocalDateTime from, LocalDateTime to) {
        Map<Long, Integer> result = new HashMap<>();
        jdbc.query("SELECT data_item_id, COUNT(*) AS cnt FROM measurement WHERE data_item_id IN (" + in(ids)
                        + ") AND measured_at >= ? AND measured_at <= ? GROUP BY data_item_id",
                rs -> {
                    result.put(rs.getLong("data_item_id"), rs.getInt("cnt"));
                }, args(ids, ts(from), ts(to)));
        return result;
    }

    @Override
    public Map<Long, List<Point>> raw(Collection<Long> ids, LocalDateTime from, LocalDateTime to) {
        Map<Long, List<Point>> result = new HashMap<>();
        jdbc.query("SELECT data_item_id, measured_at, measured_value FROM measurement WHERE data_item_id IN ("
                        + in(ids) + ") AND measured_at >= ? AND measured_at <= ? ORDER BY data_item_id, measured_at",
                rs -> {
                    result.computeIfAbsent(rs.getLong("data_item_id"), id -> new ArrayList<>()).add(new Point(
                            rs.getTimestamp("measured_at").toLocalDateTime(), rs.getDouble("measured_value")));
                }, args(ids, ts(from), ts(to)));
        return result;
    }

    @Override
    public Map<Long, List<Point>> buckets(Collection<Long> ids, LocalDateTime from, LocalDateTime to,
            LocalDateTime anchor, int bucketSeconds, boolean averaged) {
        // MySQL ONLY_FULL_GROUP_BY 는 바인딩 값이 든 식을 SELECT 와 GROUP BY 에서 같은 식으로 보지 않는다.
        // anchor 와 칸 크기는 사용자 입력이 아니라 서버가 만든 값이므로 리터럴로 넣어 두 식을 글자 그대로 같게 한다.
        String bucket = "FLOOR(TIMESTAMPDIFF(SECOND, TIMESTAMP '" + ANCHOR.format(anchor) + "', measured_at) / "
                + Math.max(1, bucketSeconds) + ")";
        String where = " FROM measurement WHERE data_item_id IN (" + in(ids) + ") AND measured_at >= ?"
                + " AND measured_at <= ?";
        String sql = averaged
                ? "SELECT data_item_id, " + bucket + " AS bucket_no, AVG(measured_value) AS v" + where
                        + " GROUP BY data_item_id, " + bucket + " ORDER BY data_item_id, bucket_no"
                // 칸마다 마지막 측정 시각을 찾고 그 시각의 값을 읽는다. (data_item_id, measured_at) 은 기본 키다.
                : "SELECT m.data_item_id, k.bucket_no, m.measured_value AS v FROM measurement m JOIN ("
                        + "SELECT data_item_id, " + bucket + " AS bucket_no, MAX(measured_at) AS last_at" + where
                        + " GROUP BY data_item_id, " + bucket + ") k"
                        + " ON m.data_item_id = k.data_item_id AND m.measured_at = k.last_at"
                        + " ORDER BY m.data_item_id, k.bucket_no";
        Map<Long, List<Point>> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            long no = rs.getLong("bucket_no");
            result.computeIfAbsent(rs.getLong("data_item_id"), id -> new ArrayList<>()).add(new Point(
                    anchor.plusSeconds(no * bucketSeconds), rs.getDouble("v")));
        }, args(ids, ts(from), ts(to)));
        return result;
    }

    @Override
    public List<Interval> disconnections(long equipmentId, LocalDateTime from, LocalDateTime to) {
        return jdbc.query("SELECT started_at, ended_at FROM equipment_status_history WHERE equipment_id = ?"
                        + " AND status = 'COMMUNICATION_LOST' AND started_at <= ? AND (ended_at IS NULL OR ended_at >= ?)"
                        + " ORDER BY started_at, history_id",
                (rs, row) -> {
                    Timestamp ended = rs.getTimestamp("ended_at");
                    return new Interval(rs.getTimestamp("started_at").toLocalDateTime(),
                            ended == null ? null : ended.toLocalDateTime());
                }, equipmentId, ts(to), ts(from));
    }

    @Override
    public boolean currentlyConnected(long equipmentId) {
        List<String> status = jdbc.queryForList("SELECT status FROM equipment_state WHERE equipment_id = ?",
                String.class, equipmentId);
        return !status.isEmpty() && !"COMMUNICATION_LOST".equals(status.get(0));
    }

    @Override
    public List<Alarm> alarms(Collection<Long> ids, LocalDateTime from, LocalDateTime to) {
        return jdbc.query("SELECT alarm_id, data_item_id, occurred_at, closed_at, trigger_value, grade FROM alarm"
                        + " WHERE data_item_id IN (" + in(ids) + ") AND occurred_at <= ?"
                        + " AND (closed_at IS NULL OR closed_at >= ?) ORDER BY occurred_at, alarm_id",
                (rs, row) -> {
                    Timestamp closed = rs.getTimestamp("closed_at");
                    return new Alarm(rs.getLong("alarm_id"), rs.getLong("data_item_id"),
                            rs.getTimestamp("occurred_at").toLocalDateTime(),
                            closed == null ? null : closed.toLocalDateTime(), rs.getDouble("trigger_value"),
                            rs.getString("grade"));
                }, args(ids, ts(to), ts(from)));
    }

    private static String in(Collection<Long> ids) {
        return String.join(",", Collections.nCopies(ids.size(), "?"));
    }

    private static Object[] args(Collection<Long> ids, Object... after) {
        List<Object> args = new ArrayList<>(ids);
        Collections.addAll(args, after);
        return args.toArray();
    }

    private static Timestamp ts(LocalDateTime time) {
        return Timestamp.valueOf(time);
    }
}
