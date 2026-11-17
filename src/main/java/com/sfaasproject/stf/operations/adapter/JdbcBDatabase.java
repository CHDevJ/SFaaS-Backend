package com.sfaasproject.stf.operations.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import java.sql.*;
import java.time.*;
import java.util.*;

public class JdbcBDatabase {
    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    final Clock clock;
    final JsonMapper json = JsonMapper.builder().build();

    public JdbcBDatabase(JdbcTemplate jdbc, TransactionTemplate tx, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    public List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    public Map<String, Object> one(String sql, Object... args) {
        var all = rows(sql, args);
        return all.isEmpty() ? null : all.get(0);
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    public long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(
                c -> {
                    var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
                    return p;
                },
                key);
        var generated =
                Objects.requireNonNull(key.getKeys()).values().stream()
                        .filter(Number.class::isInstance)
                        .map(Number.class::cast)
                        .toList();
        if (generated.size() != 1)
            throw new IllegalStateException("Expected one generated numeric ID");
        return generated.get(0).longValue();
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public static Timestamp time(OffsetDateTime at) {
        return at == null
                ? null
                : Timestamp.valueOf(
                        at.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime());
    }

    public static OffsetDateTime dateTime(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp t)
            return t.toLocalDateTime().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
        if (value instanceof LocalDateTime t)
            return t.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
        if (value instanceof OffsetDateTime t) return t;
        throw new IllegalArgumentException("Invalid database time");
    }

    public static long number(Map<String, Object> row, String key) {
        var v = row.get(key);
        return v == null ? 0 : ((Number) v).longValue();
    }

    public static String string(Map<String, Object> row, String key) {
        var v = row.get(key);
        return v == null ? null : v.toString();
    }

    public static Map<String, Object> map(Object... pairs) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i].toString(), pairs[i + 1]);
        return map;
    }

    public <T> T convert(Object value, Class<T> type) {
        return json.convertValue(value, type);
    }
}
