package com.sfaasproject.stf.operations.service;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.operations.dto.V7Contract.PageMeta;

import org.springframework.http.HttpStatus;

import tools.jackson.databind.json.JsonMapper;

import java.math.*;
import java.time.*;
import java.util.*;

public final class V7Support {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private V7Support() {}

    public static <T> T dto(Object value, Class<T> type) {
        return JSON.convertValue(value, type);
    }

    public static Map<String, Object> map(Object... pairs) {
        return com.sfaasproject.stf.operations.adapter.JdbcBDatabase.map(pairs);
    }

    public static BigDecimal dec(Object v) {
        return v == null ? null : new BigDecimal(v.toString());
    }

    public static DomainException missing(String code) {
        return new DomainException(HttpStatus.NOT_FOUND, code, "대상을 찾을 수 없습니다.");
    }

    public static void id(Long id) {
        if (id != null && id <= 0) throw Checks.invalid("식별자는 양수여야 합니다.");
    }

    public static void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw Checks.invalid("페이지와 크기를 확인해 주세요.");
    }

    public static PageMeta meta(int page, int size, int total) {
        return new PageMeta(page, size, (long) total, (int) ((total + (long) size - 1) / size));
    }

    public static <T> List<T> slice(List<T> rows, int page, int size) {
        page(page, size);
        return rows.stream().skip((long) page * size).limit(size).toList();
    }

    public record Period(OffsetDateTime from, OffsetDateTime to) {}

    public static Period period(OffsetDateTime from, OffsetDateTime to, OffsetDateTime now) {
        var start = now.atZoneSameInstant(KST).toLocalDate().atStartOfDay(KST).toOffsetDateTime();
        from = from == null ? start : from;
        to = to == null ? start.plusDays(1) : to;
        if (!to.isAfter(from))
            throw new DomainException(
                    HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "시작 시각은 종료 시각보다 빨라야 합니다.");
        return new Period(from, to);
    }

    public static boolean in(OffsetDateTime at, Period p) {
        return at != null && !at.isBefore(p.from()) && at.isBefore(p.to());
    }

    public static BigDecimal minutes(OffsetDateTime start, OffsetDateTime end) {
        return BigDecimal.valueOf(Duration.between(start, end).toNanos())
                .divide(BigDecimal.valueOf(60_000_000_000L), 12, RoundingMode.HALF_UP);
    }
}
