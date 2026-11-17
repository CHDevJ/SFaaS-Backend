package com.sfaasproject.stf.common.paging;

import com.sfaasproject.stf.common.DomainException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/**
 * 목록 API 의 page, size 쿼리. 계약(P-02): page 는 0부터, size 는 기본 20 이고 1~100.
 * 범위를 벗어나면 조용히 고치지 않고 400 VALIDATION_ERROR 로 거절한다.
 */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.");
        }
    }

    /** 값이 없으면 기본값(page 0, size 20)을 쓴다. */
    public static PageQuery of(Integer page, Integer size) {
        return new PageQuery(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page, size, sort);
    }
}
