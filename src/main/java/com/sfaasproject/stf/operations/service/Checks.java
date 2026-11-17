package com.sfaasproject.stf.operations.service;

import com.sfaasproject.stf.common.DomainException;

import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;

public final class Checks {
    private Checks() {}

    public static void required(Object value, String name) {
        if (value == null) throw invalid(name + " is required");
    }

    public static void positive(Long value, String name) {
        if (value != null && value <= 0) throw invalid(name + " must be positive");
    }

    public static void range(Integer value, int min, int max, String name) {
        if (value != null && (value < min || value > max)) throw invalid(name + " is out of range");
    }

    public static void period(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to))
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "조회 기간을 확인해 주세요.");
    }

    public static void sort(String sort) {
        if (sort != null && !sort.matches("^[A-Za-z][A-Za-z0-9]*(,asc|,desc)?$"))
            throw invalid("Invalid sort");
    }

    public static DomainException invalid(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    public static DomainException missing(String code) {
        return new DomainException(HttpStatus.NOT_FOUND, code, "대상을 찾을 수 없습니다.");
    }
}
