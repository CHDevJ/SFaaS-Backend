package com.sfaasproject.stf.common;

import org.springframework.data.domain.Page;

/** 목록 응답의 meta (openapi PageMeta). */
public record PageMeta(int page, int size, long totalElements, int totalPages) {

    public static PageMeta from(Page<?> page) {
        return new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
