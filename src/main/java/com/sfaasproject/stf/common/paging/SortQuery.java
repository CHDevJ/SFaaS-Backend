package com.sfaasproject.stf.common.paging;

import com.sfaasproject.stf.common.DomainException;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/**
 * 목록 API 의 sort 쿼리({@code field,asc} 또는 {@code field,desc})를 해석한다.
 * 허용 목록에 없는 필드나 잘못된 방향은 400 VALIDATION_ERROR 로 거절한다.
 * 같은 값끼리 순서가 흔들리지 않도록 항상 식별자를 마지막 정렬 기준으로 붙인다.
 */
public final class SortQuery {

    private SortQuery() {
    }

    /**
     * @param raw 요청의 sort 값. 없으면 기본 정렬을 쓴다
     * @param allowed API 필드 이름 -> 엔티티 속성 이름
     * @param tieBreaker 마지막 정렬 기준으로 붙일 엔티티 속성(보통 id)
     */
    public static Sort parse(String raw, Map<String, String> allowed, Sort defaultSort, String tieBreaker) {
        Sort tie = Sort.by(tieBreaker).ascending();
        if (raw == null || raw.isBlank()) {
            return defaultSort.and(tie);
        }
        String[] parts = raw.split(",", -1);
        if (parts.length > 2) {
            throw invalid();
        }
        String property = allowed.get(parts[0].strip());
        if (property == null) {
            throw invalid();
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            String value = parts[1].strip();
            if (value.equalsIgnoreCase("desc")) {
                direction = Sort.Direction.DESC;
            } else if (!value.equalsIgnoreCase("asc")) {
                throw invalid();
            }
        }
        return Sort.by(direction, property).and(tie);
    }

    private static DomainException invalid() {
        return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.");
    }
}
